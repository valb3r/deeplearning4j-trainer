
import com.valb3r.deeeplearning4j_trainer.spring.Deeplearing4jTrainerSpringApp
import com.valb3r.deeeplearning4j_trainer.spring.repository.TrainingProcessRepository
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.time.Duration

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureMockMvc
@SpringBootTest(classes = [Deeplearing4jTrainerSpringApp::class])
@ContextConfiguration(initializers = [Initializer::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TrainingIntegrationTest {

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
        mockMvc.perform(newProcess(*res).param("business-key", "test1"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        await().atMost(Duration.ofSeconds(600)).until { null != historyService.createHistoricProcessInstanceQuery().finished().processDefinitionId(processDefinitionId).singleResult() }

        val proc = trainingProcessRepo.findAll().shouldHaveSize(1).first()
        proc.completed.shouldBeTrue()
        proc.errorMessage.shouldBeNull()
        proc.getCtx().currentEpoch!!.shouldBe(1)
        proc.bestLoss!!.shouldBeGreaterThan(-1.0)
    }

    @CsvSource(
        "test-data/failure/bad-input/**,class java.lang.NullPointerException: null",
        "test-data/failure/bad-model/**,Missing required creator property 'in'",
        "test-data/failure/bad-training-cfg/**,'Instantiation of [simple type, class com.valb3r.deeeplearning4j_trainer.spring.flowable.dto.Loss]'",
        "test-data/failure/no-data-uploaded/**,No data files available",
        "test-data/failure/no-model-uploaded/**,No model or model spec available",
        "test-data/failure/no-training-cfg-uploaded/**,No train spec files available",
    )
    @WithMockUser
    @ParameterizedTest
    fun testFaultyCases(filesDir: String, expectedError: String) {
        val res = PathMatchingResourcePatternResolver().getResources(filesDir)
        mockMvc.perform(newProcess(*res).param("business-key", "test1"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        await().atMost(Duration.ofSeconds(600)).until { null != historyService.createHistoricProcessInstanceQuery().finished().processDefinitionId(processDefinitionId).singleResult() }

        val proc = trainingProcessRepo.findAll().shouldHaveSize(1).first()
        proc.completed.shouldBeTrue()
        proc.errorMessage.shouldStartWith(expectedError)
        proc.bestLoss?.shouldBeNull()
    }

    private fun newProcess(vararg files: Resource): MockHttpServletRequestBuilder {
        val upload = multipart("/user/processes/definitions/${processDefinitionId}/start")
        files.forEach {
            upload.file(MockMultipartFile("inputs", it.file.name, null, it.inputStream))
        }
        return upload.characterEncoding(UTF_8).with(csrf())
    }
}

class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    @SneakyThrows
    override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
        val properties: MutableList<String> = ArrayList()
        val tmpDir = Files.createTempDirectory("trainer-it-")
        properties.add("directories.input=${tmpDir.resolve("input").toAbsolutePath()}")
        properties.add("directories.output=${tmpDir.resolve("output").toAbsolutePath()}")
        properties.add("spring.datasource.url=jdbc:h2:file:${tmpDir.resolve("dl4j-trainer.db")}")
        TestPropertyValues.of(properties).applyTo(configurableApplicationContext.environment)
    }
}