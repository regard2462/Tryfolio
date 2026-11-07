// java.io.File auf iOS: echte Pfade im Sandkasten der App (Dateien ueber de.troetenrepublik.app.ios.Dateien).

package java.io

import de.troetenrepublik.app.ios.Dateien

class File(pfad: String) : Comparable<File> {
    constructor(parent: File?, child: String) : this(if (parent == null) child else parent.path + "/" + child)
    constructor(parent: String?, child: String) : this(if (parent == null) child else "$parent/$child")

    val path: String = normal(pfad)
    val absolutePath: String get() = path
    val canonicalPath: String get() = path
    val absoluteFile: File get() = this
    val name: String get() = path.substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val parent: String? get() = if (path == "/" || '/' !in path) null else path.substringBeforeLast('/').ifEmpty { "/" }
    val parentFile: File? get() = parent?.let { File(it) }
    val isFile: Boolean get() = Dateien.istDatei(path)
    val isDirectory: Boolean get() = Dateien.istOrdner(path)

    fun exists(): Boolean = Dateien.existiert(path)
    fun canRead(): Boolean = exists()
    fun canWrite(): Boolean = true
    fun mkdirs(): Boolean = !isDirectory && Dateien.ordnerMachen(path)
    fun mkdir(): Boolean = !isDirectory && Dateien.ordnerMachen(path)
    fun delete(): Boolean = Dateien.loeschen(path)
    fun deleteRecursively(): Boolean {
        listFiles()?.forEach { it.deleteRecursively() }
        return delete() || !exists()
    }
    fun createNewFile(): Boolean {
        if (exists()) return false
        return Dateien.schreiben(path, ByteArray(0))
    }
    fun length(): Long = Dateien.groesse(path)
    fun lastModified(): Long = Dateien.zeit(path)
    fun setLastModified(time: Long): Boolean = Dateien.zeitSetzen(path, time)
    fun renameTo(dest: File): Boolean = Dateien.umbenennen(path, dest.path)

    // Rechte setzt der Sandkasten von iOS; nur die App selbst kommt an ihre Dateien
    fun setReadable(readable: Boolean, ownerOnly: Boolean = true): Boolean = true
    fun setWritable(writable: Boolean, ownerOnly: Boolean = true): Boolean = true
    fun setExecutable(executable: Boolean, ownerOnly: Boolean = true): Boolean = true

    fun list(): Array<String>? = if (isDirectory) Dateien.kinder(path).toTypedArray() else null
    fun listFiles(): Array<File>? = if (isDirectory) Dateien.kinder(path).map { File(this, it) }.toTypedArray() else null
    fun listFiles(filter: (File) -> Boolean): Array<File>? = listFiles()?.filter(filter)?.toTypedArray()

    fun resolve(relative: String): File = File(this, relative)

    fun readBytes(): ByteArray = Dateien.lesen(path) ?: throw FileNotFoundException("$path (No such file or directory)")
    fun writeBytes(array: ByteArray) {
        if (!Dateien.schreiben(path, array)) throw IOException("$path (write failed)")
    }
    fun appendBytes(array: ByteArray) = writeBytes((Dateien.lesen(path) ?: ByteArray(0)) + array)
    fun readText(): String = readBytes().decodeToString()
    fun readText(charset: java.nio.charset.Charset): String = de.troetenrepublik.app.ios.jvm.dekodieren(readBytes(), charset)
    fun writeText(text: String) = writeBytes(text.encodeToByteArray())
    fun writeText(text: String, charset: java.nio.charset.Charset) = writeBytes(de.troetenrepublik.app.ios.jvm.kodieren(text, charset))
    fun appendText(text: String) = appendBytes(text.encodeToByteArray())
    fun readLines(): List<String> = readText().lines()
    fun forEachLine(action: (String) -> Unit) = readLines().forEach(action)
    fun inputStream(): InputStream = ByteArrayInputStream(readBytes())

    override fun compareTo(other: File): Int = path.compareTo(other.path)
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
    override fun toString(): String = path

    private companion object {
        // Doppelte und abschliessende Schraegstriche weg, . und .. aufloesen
        fun normal(p: String): String {
            val absolut = p.startsWith("/")
            val teile = ArrayList<String>()
            for (t in p.split('/')) when (t) {
                "", "." -> {}
                ".." -> if (teile.isNotEmpty() && teile.last() != "..") teile.removeAt(teile.lastIndex) else if (!absolut) teile += t
                else -> teile += t
            }
            val s = teile.joinToString("/")
            return if (absolut) "/$s" else s.ifEmpty { "." }
        }
    }
}
