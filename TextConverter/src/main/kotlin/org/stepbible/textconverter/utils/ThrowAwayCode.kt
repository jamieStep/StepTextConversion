package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

object ThrowAwayCode
{


/******************************************************************************/
fun testFindNodesByAttributeValue ()
{
  val osisDoc = Dom.getDocument("C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Miscellaneous\\Text_eng_NASB1995\\InputOsis\\SB-tagged nasb1995_osis.xml")
  val usxDoc = Dom.getDocument("C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Dbl\\BiblicaCopyright\\Text_eng_NIV\\InputUsx\\3jn.usx")
  val selectedOsis = Dom.findNodesByAttributeValue(osisDoc, "div", "type", "book")
  val selectedUsx = Dom.findNodesByAttributeValue(usxDoc, "chapter", "style", "c.?".toRegex())

  var nodeOsis = Dom.findNodeByAttributeName(osisDoc, "note", "type")
  var nodeUsx = Dom.findNodeByAttributeName(usxDoc, "verse", "number")

  while (true)
  {
    Dbg.d(Dom.toString(nodeOsis!!))
    nodeOsis = Dom.getParent(nodeOsis)
    if (null == nodeOsis) break
  }
  exitProcess(0)
}


/******************************************************************************/
fun lookForNestedParas ()
{
  var files = StepFileUtils.getMatchingFilesFromFolder("C:/Users/Jamie/RemotelyBackedUp/Git/StepTextConversion/Texts/Miscellaneous/Text_eng_NETfull2_public/InputUsx", ".+\\.usx".toRegex())
  files.forEach {filePath ->
    val fileName = File(filePath.toString()).name
    val doc = Dom.getDocument(filePath.toString())
    val paras = Dom.findNodesByName(doc, "para")

    var madeChange = false

    paras.forEach {
      if (null != Dom.findNodeByName(it, "para", false))
      {
        it["XXX"] = "XXX"
        madeChange = true
      }
    }

    if (madeChange)
    {
       println(fileName)
       Dom.outputDomAsXml(doc, filePath.toString() + "xxx", null)
    }
  }

  exitProcess(0)
}


/******************************************************************************/
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


/******************************************************************************/
fun convertLutherVLToUsx ()
{
  File("C:\\Users\\Jamie\\Desktop\\Martin_Luther_Uebersetzung_Strong_1545.txt").readLines().forEach {
  }
}


/******************************************************************************/
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


/******************************************************************************/
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