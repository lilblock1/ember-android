package dev.andes.app

import android.content.Context
import dev.andes.authentication.TokenVault
import dev.andes.core.SecretRedactingOutputStream
import org.json.JSONObject
import java.io.File
import java.io.PrintStream
import java.time.Instant
import java.util.Base64

/** Used only by the explicit authenticated startup experiment after fresh ownership checks.
 * No dummy identity, demo switch, plaintext credential files or exported launch arguments.
 */
internal class ProtectedStartup private constructor(val arguments: Array<String>, private val secrets: Set<String>) {
    fun protectConsole(): AutoCloseable {
        val previousOut = System.out
        val previousErr = System.err
        val safeOut = PrintStream(SecretRedactingOutputStream(previousOut, secrets), true, "UTF-8")
        val safeErr = PrintStream(SecretRedactingOutputStream(previousErr, secrets), true, "UTF-8")
        System.setOut(safeOut); System.setErr(safeErr)
        return AutoCloseable {
            System.setOut(previousOut); System.setErr(previousErr)
            safeOut.close(); safeErr.close()
        }
    }
    override fun toString() = "ProtectedStartup([REDACTED])"
    companion object {
        fun prepare(context: Context): ProtectedStartup {
            try {
                val session = JSONObject(requireNotNull(TokenVault(context, "minecraft-session").read()))
                require(session.getString("clientId") == dev.andes.authentication.BuildConfig.CLIENT_ID)
                require(Instant.parse(session.getString("expiresAt")) > Instant.now())
                val token = session.getString("accessToken")
                val claims = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8))
                val xuid = claims.getString("xuid")
                val profile = session.getString("profileId").replace("-", "")
                val name = session.getString("profileName")
                require(profile.matches(Regex("[0-9a-fA-F]{32}")) && name.matches(Regex("[A-Za-z0-9_]{3,16}")) && xuid.matches(Regex("[0-9]{1,32}")))
                val game = File(context.filesDir, "research-client/game-1.20.4")
                val assets = File(context.filesDir, "research-client/assets")
                check(game.mkdirs() || game.isDirectory)
                check(assets.mkdirs() || assets.isDirectory)
                val arguments = arrayOf("--username", name, "--version", "1.20.4", "--gameDir", game.absolutePath,
                    "--assetsDir", assets.absolutePath, "--uuid", profile, "--accessToken", token,
                    "--clientId", session.getString("clientId"), "--xuid", xuid, "--userType", "msa",
                    "--versionType", "release", "--width", "854", "--height", "480")
                return ProtectedStartup(arguments, setOf(token, xuid, profile, session.getString("profileId"), name))
            } catch (_: Exception) { throw IllegalStateException("Could not prepare the protected Minecraft session; private details withheld") }
        }
    }
}
