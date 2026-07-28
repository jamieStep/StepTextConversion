/******************************************************************************/
/**
 * A local server to act as a helper for my TamperMonkey additional lookups.
 *
 * This was written specifically to make it possible to pass the URLs which
 * openbible gives for GoogleEarth KML: files to Google Earth, but I'm hoping
 * it can serve other purposes too.
 *
 * SECRET represents a key value which the server expects to see before
 * running anything.  It was recommended by Claude, but is not essential
 * to the operation of the server -- this particular part of the implementation
 * could be dropped without breaking anything.
 *
 * To turn this into a native executable, Claude rode to the rescue:
 *
 * - Added to the plugins in build.gradle.kts:
 *     application
 *     id("com.gradleup.shadow") version "8.3.5" // check for latest version
 *
 *  - Run   .\gradlew.bat shadowJar   in the IDEA console.  This should
 *    create a JAR in the build/libs folder.  The name of that JAR needs to
 *    appear in the command below.  Currently it's
 *
 *        HelperForTamperMonkeyAdditionalLookups-1.0-SNAPSHOT-all.jar
 *
 *
 *  - Go to project root (ie the folder above build/libs).
 *
 *  - In a plain vanilla command window, run:
 *
 *     "C:\Program Files\Java\jdk-25.0.3\bin\jpackage.exe" ^
 *     --type app-image ^
 *     --name GoogleEarthHelper ^
 *     --input build/libs ^
 *     --main-jar HelperForTamperMonkeyAdditionalLookups-1.0-SNAPSHOT-all.jar ^
 *     --main-class org.stepbible.helperforadditionallookups.MainKt ^
 *     --win-console
 *
 *
 *
 */

package org.stepbible.helperforadditionallookups

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import kotlin.system.exitProcess


/******************************************************************************/
const val PORT = 47291 // pick an unusual port to avoid clashes
const val SECRET = "3.14159"



/******************************************************************************/
fun main (args: Array<String>)
{
  /****************************************************************************/
  parseArgs(args)



  /****************************************************************************/
  val server = HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0)



  /****************************************************************************/
  server.createContext("/ping") { exchange ->
    println("Processing ping.")
    val response = "OK"
    exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
    exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
    exchange.responseBody.use { it.write(response.toByteArray()) }
  }



  /****************************************************************************/
  server.createContext("/open") { exchange ->
    val query = exchange.requestURI.query ?: ""
    val params = query.split("&")
      .mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 } }
      .associate { (k, v) -> k to URLDecoder.decode(v, StandardCharsets.UTF_8) }

    val token = params["token"]
    val action = params["action"]
    val programName = params["programName"]
    val programParams = params["programParams"]

    val status: Int
    val response: String

    if (token != SECRET)
    {
      status = 403
      response = "Forbidden"
    }

    else
      when (action)
      {
        "runCommand" ->
        {
          val (s, r) = doRunCommand(programName, programParams)
          status = s
          response = r
        }

        else ->
        {
          val msg = "Unknown action: $action."
          System.err.println(msg)
          status = 500
          response = msg
        }
    }

    exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
    exchange.sendResponseHeaders(status, response.toByteArray().size.toLong())
    exchange.responseBody.use { it.write(response.toByteArray()) }
  } // server.createContext

  server.executor = null
  server.start()
  println("Helper running on http://127.0.0.1:$PORT — Ctrl+C to stop.  Secret: $SECRET.")
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                             Support functions                            **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
private val m_Config: MutableMap<String, String> = mutableMapOf()


/******************************************************************************/
private fun doRunCommand(programName: String?, params: String?): Pair<Int, String>
{
  /****************************************************************************/
  if (programName.isNullOrBlank())
    return Pair(400, "Missing executable parameter")



  /****************************************************************************/
  try
  {
    /**************************************************************************/
    // Stuff as passed Base64 encoded, to save worrying about encoding the URI.

    val decodedProgramName = m_Config[String(Base64.getDecoder().decode(programName), Charsets.UTF_8).replace("$", "")]
    val decodedProgramParams = if (null == params) "" else String(Base64.getDecoder().decode(params), Charsets.UTF_8)

    if (null == decodedProgramName)
    {
      val msg = "Failed to find program path for: $programName."
      println(msg)
      return Pair(500, "Failed to launch $msg.")
    }



    /**************************************************************************/
    println("Running: $decodedProgramName $decodedProgramParams")
    ProcessBuilder(decodedProgramName, decodedProgramParams).start()
    return Pair(200, "OK")
  }

  catch (e: Exception)
  {
    return Pair(500, "Failed to launch: ${e.message}")
  }
}


/******************************************************************************/
private fun getDefaultConfigFilePath (): Path?
{
  val codeSource = object {}.javaClass.protectionDomain.codeSource
  val location = Paths.get(codeSource.location.toURI())
  val folderPath = if (Files.isRegularFile(location)) location.parent else location
  val res = Paths.get(folderPath.toString(), "additionalBibleLookupsConfig.txt")
  return if (Files.exists(res)) res else null
}


/******************************************************************************/
private fun getProgramName(): String
{
  val info = ProcessHandle.current().info()
  val command = info.command().orElse(null)
  return if (command != null) Paths.get(command).fileName.toString() else "???"
}


/******************************************************************************/
private fun parseArgs (args: Array<String>)
{
  /***************************************************************************/
  var configPath: Path? = getDefaultConfigFilePath()
  var i = 0



  /***************************************************************************/
  while (i < args.size) {
    when (args[i]) {
      "-help" -> {
        printHelp()
        exitProcess(0)
      }
      "-config" -> {
        if (i + 1 >= args.size) {
          System.err.println("Error: -config requires a path argument.")
          exitProcess(1)
        }
        configPath = Paths.get(args[i + 1])
        i++
        // consume the path argument too
        if (!Files.exists(configPath))
        {
          System.err.println("Error: -config file does not exist.")
          exitProcess(1)
        }
      }
      else ->
      {
        System.err.println("Unknown argument: ${args[i]}")
        printHelp()
        exitProcess(1)
      }
    }
    i++
  }



  /***************************************************************************/
  try
  {
    println("Taking configration data from $configPath.")
    if (null != configPath)
      readConfigFile(configPath)
  }
  catch (_: Exception)
  {
    if (configPath!!.toString() != getDefaultConfigFilePath().toString()) {
      System.err.println("Error: Couldn't read config file $configPath.")
      exitProcess(1)
    }
  }
}


/******************************************************************************/
private fun printHelp()
{
  println("""
    Local server used to support the 'Additional lookups' functionality for use
    with selected Bible websites.

    Usage:
      ${getProgramName()} [-config <path>] [-help]

    Options:
       -config <path>   Path to the config file. Defaults to 'config.txt' in the
                        same folder as this executable.  Optional -- needed only
                        if you want to be able to use the functionality which
                        lets you run local executables such as GoogleEarth.
    """.trimIndent())

  exitProcess(0)
}


/******************************************************************************/
fun readConfigFile (path: Path)
{
  /****************************************************************************/
  if (!Files.exists(path))
  {
    System.err.println("Error: Config file not found at $path — continuing with no config values.")
    exitProcess(1)
  }



  /****************************************************************************/
  var errors = 0

  Files.readAllLines(path).forEachIndexed { lineNumber, rawLine ->
    val line = rawLine.trim()

    if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

    val separatorIndex = line.indexOf('=')
    if (separatorIndex < 0) {
      ++errors
      System.err.println("Error: ignoring malformed config line ${lineNumber + 1}: $rawLine")
      return@forEachIndexed
    }

    val key = line.substring(0, separatorIndex).trim()
    val value = line.substring(separatorIndex + 1).trim()
    m_Config[key] = value
  }

  if (errors > 0)
    throw Exception("")
}