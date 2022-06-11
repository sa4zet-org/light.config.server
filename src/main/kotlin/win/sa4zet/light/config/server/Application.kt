package win.sa4zet.light.config.server

import com.typesafe.config.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.io.File
import java.util.*
import kotlin.system.exitProcess

data class CallInfo(
    val userName: String,
    val remoteHost: String,
    val branch: String,
    val configFileName: String,
    val configSubPath: String,
    val method: String,
    val parentDir: String,
    val inRaw: Boolean,
    val prettyPrint: Boolean
)

val contentTypeHocon = ContentType("application", "hocon")
val salt: String = System.getenv("config_server_basic_auth_salt") ?: throw IllegalStateException("You have to set the 'config_server_basic_auth_salt' environment variable!")
val digestFunction = getDigestFunction("SHA-512") { salt }
var extraConfig: Config = ConfigFactory.defaultApplication()

fun main(args: Array<String>) {
    if (salt.isBlank()) {
        System.err.println("You have to set the 'config_server_basic_auth_salt' environment variable!")
        exitProcess(1)
    }
    if (args.isNotEmpty()) {
        if (args.first() == "digest") {
            var pwd: String?
            while (true) {
                print("Enter a not empty password: ")
                pwd = readLine()
                if (pwd == null) {
                    println("\nBye!")
                    return
                }
                if (pwd.isBlank()) continue
                else println(String(Base64.getEncoder().encode(digestFunction.invoke(pwd))))
            }
        }
    }
    val cfgPath: String = System.getenv("config_server_config_path") ?: throw IllegalStateException("You have to set the 'config_server_config_path' environment variable!")
    extraConfig = ConfigFactory.parseFile(File(cfgPath)).resolve()
    git.sync("master")
    EngineMain.main(args)
}

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    val hashedUserTable = UserHashedTableAuth(
        table = extraConfig.getConfigList("auth.basic.users").associate { it.getString("userName") to it.getString("userHash").decodeBase64Bytes() },
        digester = digestFunction
    )

    install(DefaultHeaders) {
        header(HttpHeaders.Accept, contentTypeHocon.toString())
        header(HttpHeaders.Server, "Configuration Server")
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        anyHost()
    }
    install(Compression) {
        gzip {
            priority = 1.0
        }
    }
    install(StatusPages) {
        exception<NotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound)
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<ConfigException> { call, ex ->
            call.respondText(
                text = "Invalid configuration: ${ex.message}",
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.InternalServerError
            )
        }
        exception<Throwable> { call, ex ->
            call.application.log.error("ouch :(", ex)
            call.respond(HttpStatusCode.InternalServerError)
            throw ex
        }
    }
    install(Authentication) {
        basic("basic-auth") {
            realm = "Config Server"
            validate { hashedUserTable.authenticate(it) }
        }
    }
    install(Routing) {
        trace { application.log.trace(it.buildText()) }
        get("/health_check") {
            call.respondText(
                text = "ok",
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.OK
            )
        }
        authenticate("basic-auth") {
            get("/cfg/{branch}/{path...}") {
                val callInfo = makeCallInfo(call)
                call.respondText(
                    text = getValue(callInfo),
                    contentType = if (callInfo.inRaw) contentTypeHocon else ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }
        }
        static("/") {
            resource("/favicon.ico", "favicon.svg", "static")
            staticBasePackage = "static"
            resources("/")
        }
    }
}

private fun getValue(callInfo: CallInfo): String {
    var cfg = parseConfigFile(callInfo)
    if (!callInfo.inRaw) cfg = cfg.resolve()
    if (callInfo.configSubPath.isNotEmpty()) {
        if (!cfg.hasPath(callInfo.configSubPath)) throw NotFoundException()
        val value = cfg.getAnyRef(callInfo.configSubPath)
        return if (value is HashMap<*, *>) {
            renderHocon(cfg.getObject(callInfo.configSubPath), callInfo.inRaw, callInfo.prettyPrint)
        } else {
            value.toString()
        }
    }
    return renderHocon(cfg, callInfo.inRaw, callInfo.prettyPrint)
}

private fun parseConfigFile(callInfo: CallInfo): Config {
    return ConfigFactory.parseFile(gitGetHoconFile(callInfo.branch, parentDir = callInfo.parentDir, fileName = callInfo.configFileName))
}

private fun makeCallInfo(call: ApplicationCall): CallInfo {
    val principal: UserIdPrincipal? = call.authentication.principal()
    val branch = call.parameters["branch"]!!
    val path = call.parameters.getAll("path")!!
    if (path.isEmpty()) throw BadRequestException("")
    val callInfo = CallInfo(
        userName = principal!!.name,
        remoteHost = call.request.local.remoteHost,
        branch = branch,
        parentDir = path[0],
        configFileName = path[1],
        configSubPath = path.drop(2).joinToString("."),
        method = call.request.httpMethod.value,
        inRaw = call.request.queryParameters.contains("inRaw"),
        prettyPrint = call.request.queryParameters.contains("prettyPrint")
    )
    call.application.log.info("${callInfo.userName} tried to ${callInfo.method} ${callInfo.configFileName}{${callInfo.configSubPath}} from ${callInfo.remoteHost}")
    return callInfo
}
