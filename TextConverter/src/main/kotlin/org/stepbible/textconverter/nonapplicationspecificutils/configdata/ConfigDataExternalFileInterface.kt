/****************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils.forceToSingleLine
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.*
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


/****************************************************************************/
/**
 * Classes to handle 'external' metadata.  By this I mean data supplied in a
 * form other than my own configuration format -- for example in the XML form
 * used by DBL.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                              Base class                                  **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
abstract class ConfigDataExternalFileInterfaceBase
{
  /****************************************************************************/
  /**
   * Returns the value associated with the given parameter..
   *
   * @param selector "metadata" or "licence" -- lets us take data from one of
   *   two files where available.
   *
   * @param parms Whatever is meaningful to the processor in terms of obtaining a value.
   *
   * @return The value obtained.
   */

  abstract fun getValue (selector: String, vararg parms: String): String?



  /****************************************************************************/
  /**
  * Returns the full list of book details (abbreviations etc).  May not be
  * available from all sources.
  *
   * @param selector "metadata" or "licence" -- lets us take data from one of
   *   two files where available.
   *
   * @param parms Whatever is meaningful to the processor in terms of obtaining a value.
   *
   * @return The value obtained.
   */

  abstract fun getValueList (selector: String, vararg parms: String): List<Node>?


  /****************************************************************************/
  open fun initialise () {}
}





/****************************************************************************/
/****************************************************************************/
/**                                                                        **/
/**               Externally visible support functionality                 **/
/**                                                                        **/
/****************************************************************************/
/****************************************************************************/

/****************************************************************************/
open class ConfigDataExternalFileInterfaceXml: ConfigDataExternalFileInterfaceBase()
{
  /****************************************************************************/
  override fun initialise()
  {
    super.initialise()

    for (key in listOf("stepExternalMetadataFileName", "stepExternalLicenceFileName"))
    {
      var x = ConfigData[key] ?: continue
      val selector = key.replace("stepExternal", "").replace("FileName", "").lowercase()
      if (!x.startsWith("@find")) x = "@find/$x"
      m_Files[selector] = Dom.getDocument(FileLocations.getInputPath(x)!!)
    }
  }


  /****************************************************************************/
  /* Called when someone has done a ConfigData.get and the data which has been
     retrieved includes $getExternal.  This arranges to do the donkey work to
     obtain the data from the external source. */

  override fun getValue (selector: String, vararg parms: String): String?
  {
    /************************************************************************/
    /* Check if we're getting an attribute. */

    var attribute: String? = null
    var xpath = parms[0]

    if ("/@" in xpath)
    {
      val x = xpath.split("/@")
      xpath = x[0]
      attribute = x[1]
    }



    /************************************************************************/
    val doc = m_Files[selector] ?: return null
    //Dbg.d(doc.documentElement.nodeName)
    val node = try { // Have to allow for the possibility that a given element may not exist.  In DBL, eg nameLocal may appear in a variety of locations, and we may initially have tried the wrong one.
      m_XPath.compile(xpath).evaluate(doc, XPathConstants.NODE) as Node
    }
    catch (_: Exception)
    {
      return null
    }




    /************************************************************************/
    return if (null == attribute)
      forceToSingleLine(Dom.getNodeContentAsString(node, false))!!
    else
      return Dom.getAttribute(node, attribute)
  }


  /****************************************************************************/
  override fun getValueList (selector: String, vararg parms: String): List<Node>?
  {
    val doc = m_Files["metadata"] ?: return null
    return (m_XPath.compile(parms[0]).evaluate(doc, XPathConstants.NODESET) as NodeList).toList()
  }


  /****************************************************************************/
  private val m_Files: MutableMap<String, Document> = mutableMapOf()
  private val m_XPath = XPathFactory.newInstance().newXPath()!!
}




/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**              Code to extract data from DBL metadata files                **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

object ConfigDataExternalFileInterfaceDbl: ConfigDataExternalFileInterfaceXml(), ObjectInterface
{
  override fun initialise()
  {
    super.initialise()
    getBookList()
  }


  /****************************************************************************/
  override fun getValue (selector: String, vararg parms: String): String?
  {
    var res = super.getValue(selector, *parms)
    if ("DBLMetadata/language/iso" == parms[0] && "eng".equals(res, ignoreCase = true)) res = "en" // Need 2-char version so Bibles are presented in the 'English Bibles' section of STEP's LoadBible screen.
    return res
  }


  /****************************************************************************/
  private fun getBookList ()
  {
    /**************************************************************************/
    val bookDetails = getValueList("metadata", "DBLMetadata/names/name") ?: return



    /**************************************************************************/
    val bookList: MutableList<Pair<String, MutableMap<String, String>>> = mutableListOf()
    for (nameNode in bookDetails)
    {
      val ubsName = nameNode["id"]!!.split("-")[1]
      val vernacularNames: MutableMap<String, String> = HashMap()
      val vernacularNameNodes = nameNode.childNodes
      for (j in 0..< vernacularNameNodes.length)
      {
        val nameLength = vernacularNameNodes.item(j).nodeName
        if ("#text" != nameLength) vernacularNames[nameLength] = vernacularNameNodes.item(j).firstChild.nodeValue
      }

      bookList.add(Pair(ubsName, vernacularNames))
    }



    /**************************************************************************/
    ConfigData.clearVernacularBibleDetails()
    for (p in bookList)
    {
      var s = "#VernacularBookDetails " + p.first.uppercase() + ":= "
      for (nameLength in p.second.keys) s += (StepStringUtils.safeSentenceCase(nameLength) + ":= " + p.second[nameLength]) + "; "
      ConfigData.processVernacularBibleDetails(s.substring(0, s.length - 2))
    }
  }
}
