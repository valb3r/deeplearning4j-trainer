
import com.valb3r.deeplearning4j_trainer.Deeplearning4j_TrainerSpringApp
import com.valb3r.deeplearning4j_trainer.repository.TrainingProcessRepository
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import lombok.SneakyThrows
import org.awaitility.Awaitility.await
import org.flowable.engine.HistoryService
import org.flowable.engine.RepositoryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mapdb.DB.Keys.type
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.shaded.org.bouncycastle.asn1.cms.CMSAttributes.contentType
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.time.Duration

@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureMockMvc
@SpringBootTest(classes = [Deeplearning4j_TrainerSpringApp::class])
@ContextConfiguration(initializers = [Initializer::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FileSystemTrainingIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var repositoryService: RepositoryService

    @Autowired
    private lateinit var historyService: HistoryService

    @Autowired
    private lateinit var trainingProcessRepo: TrainingProcessRepository

    private lateinit var processDefinitionId: String

    @BeforeEach
    fun selectProcess() {
        processDefinitionId = repositoryService.createProcessDefinitionQuery()
            .orderByProcessDefinitionVersion().desc()
            .listPage(0, 100)
            .first { it.name == "model-training-process" }
            .id
    }

    @CsvSource(
        "test-data/success/text-based/**",
        "test-data/success/flatbuf-based/**"
    )
    @WithMockUser
    @ParameterizedTest
    fun testModelUploadAndTrainingWorks(filesDir: String) {
        val res = PathMatchingResourcePatternResolver().getResources(filesDir)
        mockMvc.perform(newProcess(processDefinitionId, "test1", *res))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        await().atMost(Duration.ofSeconds(600)).until { null != historyService.createHistoricProcessInstanceQuery().finished().processDefinitionId(processDefinitionId).singleResult() }

        val proc = trainingProcessRepo.findAll().shouldHaveSize(1).first()
        proc.completed.shouldBeTrue()
        proc.errorMessage.shouldBeNull()
        proc.getCtx()!!.currentEpoch.shouldBe(1)
        proc.bestLoss!!.shouldBeGreaterThan(-1.0)
    }

    @CsvSource(
        "test-data/failure/bad-input/**,class java.lang.NullPointerException: null",
        "test-data/failure/bad-model/**,Missing required creator property 'in'",
        "test-data/failure/bad-training-cfg/**,'Instantiation of [simple type, class com.valb3r.deeplearning4j_trainer.flowable.dto.Loss]'",
        "test-data/failure/no-data-uploaded/**,No data files available",
        "test-data/failure/no-model-uploaded/**,No model or model spec available",
        "test-data/failure/no-training-cfg-uploaded/**,No train spec files available",
    )
    @WithMockUser
    @ParameterizedTest
    fun testFaultyCases(filesDir: String, expectedError: String) {
        val res = PathMatchingResourcePatternResolver().getResources(filesDir)
        mockMvc.perform(newProcess(processDefinitionId, "test1", *res))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        await().atMost(Duration.ofSeconds(600)).until { null != historyService.createHistoricProcessInstanceQuery().finished().processDefinitionId(processDefinitionId).singleResult() }

        val proc = trainingProcessRepo.findAll().shouldHaveSize(1).first()
        proc.completed.shouldBeTrue()
        proc.errorMessage.shouldStartWith(expectedError)
        proc.bestLoss?.shouldBeNull()
    }
}

class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    @SneakyThrows
    override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
        val properties: MutableList<String> = ArrayList()
        val tmpDir = Files.createTempDirectory("trainer-it-")
        properties.add("directories.input=file://${tmpDir.resolve("input").toAbsolutePath()}")
        properties.add("directories.output=file://${tmpDir.resolve("output").toAbsolutePath()}")
        TestPropertyValues.of(properties).applyTo(configurableApplicationContext.environment)
    }
}