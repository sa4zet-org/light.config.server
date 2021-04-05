package win.sa4zet.light.config.server

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions


fun renderHocon(config: Config, inRaw: Boolean = false, prettyPrint: Boolean = true): String {
    return renderHocon(config.root(), inRaw, prettyPrint)
}

fun renderHocon(configObject: ConfigObject, inRaw: Boolean, prettyPrint: Boolean): String {
    return configObject.render(ConfigRenderOptions.concise().setJson(!inRaw).setFormatted(prettyPrint))
}
