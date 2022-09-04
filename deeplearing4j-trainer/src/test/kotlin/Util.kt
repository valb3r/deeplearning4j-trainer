import org.springframework.core.io.Resource
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.nio.charset.StandardCharsets

// Hacketty hacks as multipart filter is disabled in app
fun newProcess(processDefinitionId: String, businessKey: String, vararg files: Resource): MockHttpServletRequestBuilder {
    val boundary = "---boundary"

    var content = ByteArray(0)
    files.forEach {
        content += createFileContent(it.inputStream.use { it.readBytes() }, boundary, "text/UTF-8", it.filename!!)
    }
    content += createFieldContent(businessKey, boundary, "business-key")
    content += "--$boundary--".toByteArray(StandardCharsets.UTF_8)

    val upload = MockMvcRequestBuilders.multipart("/user/processes/definitions/${processDefinitionId}/start")
        .contentType("multipart/form-data; boundary=$boundary")
        .content(content)

    return upload.characterEncoding(StandardCharsets.UTF_8).with(SecurityMockMvcRequestPostProcessors.csrf())
}

fun createFileContent(data: ByteArray, boundary: String, contentType: String, fileName: String): ByteArray {
    val start = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"inputs\"; filename=\"$fileName\"\r\n" +
            "Content-type: $contentType\r\n\r\n"
    return start.toByteArray(StandardCharsets.UTF_8) + data + "\r\n".toByteArray(StandardCharsets.UTF_8)
}

fun createFieldContent(data: String, boundary: String, name: String): ByteArray {
    val start = "\r\n--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n"
    return start.toByteArray(StandardCharsets.UTF_8) + data.toByteArray(StandardCharsets.UTF_8) + "\r\n".toByteArray(
        StandardCharsets.UTF_8
    )
}