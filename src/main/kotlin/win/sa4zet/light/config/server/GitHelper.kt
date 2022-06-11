package win.sa4zet.light.config.server

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.JSch
import io.ktor.server.plugins.NotFoundException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory
import org.eclipse.jgit.util.Base64
import org.eclipse.jgit.util.FS
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun Git.sync(branch: String) {
    this.fetch()
        .setForceUpdate(true)
        .setRefSpecs("+refs/heads/*:refs/remotes/origin/*")
        .call()
    this.reset()
        .setMode(ResetCommand.ResetType.HARD)
        .setRef("refs/remotes/origin/$branch")
        .call()
}

val git = getGitRepository()

private fun getGitRepository(): Git {
    SshSessionFactory.setInstance(object : JschConfigSessionFactory() {
        override fun createDefaultJSch(fs: FS?): JSch {
            val jsch = JSch()
            extraConfig.getStringList("git.remote.hostKeys").forEach {
                jsch.hostKeyRepository.add(HostKey(extraConfig.getString("git.remote.host"), Base64.decode(it.trimIndent())), null)
            }
            val privateKey = extraConfig.getString("git.remote.privateKey").trimIndent()
            jsch.identityRepository.add(privateKey.toByteArray())
            return jsch
        }
    })
    val localDir: Path = Paths.get(extraConfig.getString("git.local.dir"))
    val local: File = localDir.resolve(".git").toFile()
    if (!local.exists()) {
        Git.cloneRepository()
            .setURI(extraConfig.getString("git.remote.url"))
            .setDirectory(localDir.toFile())
            .call()
    }
    return Git(FileRepositoryBuilder.create(local))
}

val lock = ReentrantLock()

fun gitGetHoconFile(branch: String, parentDir: String, fileName: String): File = lock.withLock {
    git.sync(branch)
    val file = git.repository.workTree.resolve(parentDir).resolve("$fileName.conf").absoluteFile
    if (!file.exists()) throw NotFoundException("$fileName config not found!")
    return file
}
