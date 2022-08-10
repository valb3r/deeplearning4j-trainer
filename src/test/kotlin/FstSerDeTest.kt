
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.valb3r.deeeplearning4j_trainer.spring.flowable.FstSerDe
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nd4j.shade.guava.io.Files
import org.nd4j.shade.guava.io.Resources
import java.io.File

internal class FstSerDeTest {

    @Test
    fun testSerializationDeserialization(@TempDir inpDirPath: File) {
        val inpFile = inpDirPath.resolve("data.csv")
        Resources.asByteSource(Resources.getResource("data.csv")).copyTo(Files.asByteSink(inpFile))
        val buff = FstSerDe()
        val inpBinFile = buff.csvToBin(inpFile.absolutePath, ".bin", CsvMapper())
        val iter = FstSerDe.FstIterator(inpBinFile.fstFileName)
        val rows = mutableListOf<Map<String, FloatArray>>()
        iter.forEachRemaining { rows += it }
        rows.shouldHaveSize(9)

        rows[0]["phaseDelta"]!!.asList().shouldHaveSingleElement(7.2784454E-4f)
        rows[0]["avgAmplitude"]!!.asList().shouldHaveSingleElement(0.052124035f)
        rows[0]["inputLeft"]!!.asList().shouldHaveSize(512)
        rows[0]["inputLeft"]!![511].shouldBeEqualComparingTo(-0.101082f)
        rows[0]["expected"]!!.asList().shouldHaveSize(512)
        rows[0]["expected"]!![0].shouldBeEqualComparingTo(-0.063451454f)

        rows[8]["phaseDelta"]!!.asList().shouldHaveSingleElement(0.0027574552f)
        rows[8]["avgAmplitude"]!!.asList().shouldHaveSingleElement(0.052350927f)
        rows[8]["inputLeft"]!!.asList().shouldHaveSize(512)
        rows[8]["inputLeft"]!![511].shouldBeEqualComparingTo(0.017391685f)
        rows[8]["expected"]!!.asList().shouldHaveSize(512)
        rows[8]["expected"]!![0].shouldBeEqualComparingTo(-0.1209673f)
    }
}