package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.builders.SpecialBuilder
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import java.io.File
import java.net.URL
import java.util.jar.Manifest
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

/****************************************************************************/
/**
 * Just a location for experimental code.  Does not form part of the system
 * proper.
 *
 * @author ARA "Jamie" Jamieson
 */

object ThrowAwayCode
{
  /****************************************************************************/
  fun testCharacterEntitySize ()
  {
    var doc = Dom.getDocumentFromText("<a>&#8212;</a>",false)
    println(doc.documentElement.textContent.length)
    exitProcess(0)
  }


  /****************************************************************************/
  fun testGetVersion ()
  {
    val clazzA = object {}.javaClass.enclosingClass
    val manifestA = clazzA?.protectionDomain?.codeSource?.location?.let { location ->
      location.openStream().use { inputStream ->
        java.util.jar.JarInputStream(inputStream).manifest
      }
    }

    Dbg.d("Version number ...")
    Dbg.d(manifestA?.mainAttributes?.getValue("Implementation-Version"))

    val clazz: Class<*> = this::class.java
    val className = clazz.simpleName + ".class"
    val classPath = clazz.getResource(className)?.toString() ?: return
    if (!classPath.startsWith("jar")) {
      // Class not from JAR
      Dbg.d("Premature")
      exitProcess(0)
    }

    val manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF"
    val manifest = Manifest(URL(manifestPath).openStream())
    val attr = manifest.mainAttributes
    val value: String = attr.getValue("Implementation-Version")
    Dbg.d("Hi"); Dbg.d(value)
    exitProcess(0)
  }


  /****************************************************************************/
  fun testReflection ()
  {
    Dbg.d("JAR: ${MiscellaneousUtils.getJarFileName()}")
    Dbg.d("Package: ${SpecialBuilder::class.java.getPackage().name}")
    Dbg.d(MiscellaneousUtils.getPackageName (SpecialBuilder::class))
    try {

    val list = MiscellaneousUtils.getSubtypes(SpecialBuilder::class.java).map { it.kotlin.objectInstance!! as SpecialBuilder }
    Dbg.d(list.size)
    }
    catch(e:Exception)
    {
      Dbg.d(e.message)
    }
    exitProcess(1)
  }


  /****************************************************************************/
  fun convertNivToVL ()
  {
    var line = ""
    File("C://Users//Jamie//Desktop//niv.txt").bufferedWriter().use { writer ->
      Dom.getDocument("C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Dbl\\Biblica\\Text_eng_NIrV\\Osis\\osis.xml").getAllNodesBelow().forEach {
       if ("verse" == Dom.getNodeName(it) && "sID" in it)
       {
         line = line.replace("\\s+".toRegex(), " ").trim()
         if (line.isNotEmpty()) { writer.write(line); writer.write("\n") }
         line = it["osisID"]!! + "\t"
       }

      if (Dom.isTextNode(it) && Osis_FileProtocol.isCanonicalNode(it))
        line += " ${it.textContent}"
    }

    line = line.replace("\\s+".toRegex(), " ").trim()
    if (line.isNotEmpty()) { writer.write(line); writer.write("\n") }
  }

  exitProcess(0)
}


  /****************************************************************************/
  fun convertLutherVLToUsx ()
  {
    File("C:\\Users\\Jamie\\Desktop\\Martin_Luther_Uebersetzung_Strong_1545.txt").readLines().forEach {
    }
  }


  /****************************************************************************/
  /**
   * For trying out XSLT.
   */

  fun tryXslt ()
  {
    val document = Dom.getDocument("C:\\Users\\Jamie\\Desktop\\Test\\test.usx", true)
    val basicStylesheet: String = """
     <xsl:template match="para[@style = 'qd']">
        <xsl:copy>
              <xsl:attribute name="style">d</xsl:attribute>
      <xsl:apply-templates select="@*[not(local-name()='style')]|node()"/>
                </xsl:copy>
      </xsl:template>
     """.trimIndent()

    val newDoc = Dom.applyBasicStylesheet(document, basicStylesheet)
    Dbg.outputDom(newDoc)
    exitProcess(0)
  }


  /****************************************************************************/
  /**
   * Validates the structure of an XML file.
   */

  fun validateXmlFileStructure ()
  {
    val xmlFile = "c:/Users/Jamie/RemotelyBackedUp/Git/StepTextConversion/Texts/Miscellaneous/Text_eng_ESV_th/RawOSIS/ESV2016_OSIS+Strongs5.xml"
    try
    {
      val factory = DocumentBuilderFactory.newInstance()
      factory.isIgnoringElementContentWhitespace = true
      factory.isValidating = false
      factory.isNamespaceAware = true

      val builder = factory.newDocumentBuilder()
      builder.parse(xmlFile)
    }
    catch (e: Exception)
    {
      e.printStackTrace()
    }

    exitProcess(0)
  }
}