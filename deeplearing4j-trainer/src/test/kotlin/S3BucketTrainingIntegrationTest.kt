
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.valb3r.deeplearning4j_trainer.Deeplearning4j_TrainerSpringApp
import com.valb3r.deeplearning4j_trainer.repository.TrainingProcessRepository
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration


private val awsCreds = BasicAWSCredentials("PUB-1234567890", "SECRET-1234567890")
private val bucket = "test-bucket"

@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureMockMvc
@SpringBootTest(classes = [Deeplearning4j_TrainerSpringApp::class])
@ContextConfiguration(initializers = [S3Initializer::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class S3BucketTrainingIntegrationTest {

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
}

class S3Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    @SneakyThrows
    override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
        val properties: MutableList<String> = ArrayList()

        val minio: GenericContainer<*> = GenericContainer("minio/minio")
            .withExposedPorts(9000)
            .withEnv("MINIO_ACCESS_KEY", awsCreds.awsAccessKeyId)
            .withEnv("MINIO_SECRET_KEY", awsCreds.awsSecretKey)
            .withCommand("server /data")
            .waitingFor(Wait.defaultWaitStrategy())
        minio.start()

        val hostId = "localhost:${minio.firstMappedPort}"
        val endpoint = "http://$hostId/"
        val client: AmazonS3 = AmazonS3ClientBuilder.standard()
            .withPathStyleAccessEnabled(true)
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(endpoint, "us-east-1"))
            .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(awsCreds.awsAccessKeyId, awsCreds.awsSecretKey)))
            .build()

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).untilAsserted {
            client.createBucket(bucket)
        }
        properties.add("directories.input=s3://$hostId/$bucket/input")
        properties.add("directories.output=s3://$hostId/$bucket/output")
        properties.add("s3.access-key-id=${awsCreds.awsAccessKeyId}")
        properties.add("s3.secret-key=${awsCreds.awsSecretKey}")
        properties.add("s3.region=us-west-1")

        TestPropertyValues.of(properties).applyTo(configurableApplicationContext.environment)
    }
}