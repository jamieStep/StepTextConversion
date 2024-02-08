/****************************************************************************/
package org.stepbible.textconverter.support.configdata

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.StepStringUtils
import org.stepbible.textconverter.support.miscellaneous.StepStringUtils.forceToSingleLine
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.stepexception.StepException
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
   * @param parms Whatever is meaningful to the processor in terms of obtaining a value.
   * @return The value obtained
   */

  internal abstract fun getValue (parms: String): String?


  /****************************************************************************/
  /**
   * Initialises the data store by reading data from a given file.
   *
   * @param filePath File from which to obtain data.
   * @param callingFilePath The path of the file being processed when this
   *        method was invoked (used when trying to resolve relative paths).
   */

  internal abstract fun initialise (filePath: String, callingFilePath: String?)


  /****************************************************************************/
  internal var m_CallingFilePath: String? = null
  internal var m_IsInUse = false
  internal var m_OkIfNotExists = false
  internal var m_Path = ""
  internal var m_Status = ConfigDataExternalFileInterface.Status.NotTried
}




/****************************************************************************/
/****************************************************************************/
/**                                                                        **/
/**               Externally visible support functionality                 **/
/**                                                                        **/
/****************************************************************************/
/****************************************************************************/

/****************************************************************************/
object ConfigDataExternalFileInterface
{
  /**************************************************************************/
  enum class Status { NotTried, Ok, Failed, FailedButButDontWorry }



  /****************************************************************************/
  /* Called when someone has done a ConfigData.get and the data which has been
     retrieved includes @getExternal.  This arranges to do the donkey work to
     obtain the data from the external source. */

  fun getData (selector: String, xpath: String): String?
  {
    /**************************************************************************/
    //Dbg.d(xpath, "DBLMetadata/agencies/rightsHolder/name")



    /**************************************************************************/
    /* We're accessing a logical name which hasn't been defined. */

    val processor = m_ConfigDataExternalProcessors[selector] ?: throw StepException("External data processor not defined: $selector")



    /**************************************************************************/
    /* We have a file which has yet to be opened. */

    if (Status.NotTried == processor.m_Status)
    {
      try
      {
        processor.initialise(processor.m_Path, processor.m_CallingFilePath)
        processor.m_Status = Status.Ok
      }
      catch (_: Exception)
      {
        processor.m_Status = if (processor.m_OkIfNotExists) Status.FailedButButDontWorry else Status.Failed
        if (Status.FailedButButDontWorry != processor.m_Status) throw StepException("Failed to open external data source: $selector / ${processor.m_Path}")
      }
    }



    /**************************************************************************/
    return when (processor.m_Status)
    {
      Status.Ok                    -> getValue(selector, xpath)
      Status.Failed                -> null // Can't actually hit this, because we throw an exception on failure.
      Status.FailedButButDontWorry -> null // This was subject to an 'ifExists', and the file didn't exist.
      else                         -> null // Can't hit this because we weeded out the only other case (NotTried) a few lines ago.
    }
  }


  /****************************************************************************/
  /**
   * Given the details extracted from a parsed line, returns the corresponding
   * value, or the default if there is no corresponding value, or null if the
   * default is null too.
   */

  fun getValue (selector: String, xpath: String): String?
  {
    val processor = m_ConfigDataExternalProcessors[selector]!!
    return forceToSingleLine(processor.getValue(xpath))
  }


  /****************************************************************************/
  /**
  * Called to process a stepExternalDataSource line.  The source line should
  * look something like:
  *
  *     stepExternalDataSource[IfExists]=dbl:metadata:$metadata/metadata.xml
  *
  * where dbl should be one of the recognised external data types (currently
  * only dbl, in fact), metadata is a logical name to be associated with the
  * file, and can be pretty much anything you like (no meaning is ascribed
  * to the name), and the remainder is the file path.
  *
  * @param sourceLine The line to be processed.
  * @param callingFilePath The path of the file being processed when this
  *        method was invoked (used when trying to resolve relative paths).
  */

  fun recordDataSourceMapping (sourceLine: String, callingFilePath: String)
  {
    var parts = sourceLine.split("=")
    val ifExists = parts[0].lowercase().contains("ifexists")

    parts = parts[1].split(":")
    val type        = parts[0].trim()
    val logicalName = parts[1].trim()
    val path        = parts[2].trim()

    val existingEntry = m_ConfigDataExternalProcessors[logicalName]
    if (null != existingEntry && existingEntry.m_IsInUse) return

    val newEntry = makeConcreteInstance(type)
    newEntry.m_Path = path
    newEntry.m_CallingFilePath = callingFilePath
    newEntry.m_Status = Status.NotTried
    newEntry.m_OkIfNotExists = ifExists
    m_ConfigDataExternalProcessors[logicalName] = newEntry
  }


  /**************************************************************************/
  private fun makeConcreteInstance (type: String): ConfigDataExternalFileInterfaceBase
  {
    return when (type.lowercase())
    {
      "dbl" -> return ConfigDataExternalFileInterfaceDbl()
      else  -> throw StepException("Unknown external data type: $type")
    }
  }


  /**************************************************************************/
  private val m_ConfigDataExternalProcessors: MutableMap<String, ConfigDataExternalFileInterfaceBase> = TreeMap(String.CASE_INSENSITIVE_ORDER)
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**             Common functionality for XML-based config data               **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

abstract class ConfigDataExternalFileInterfaceXmlCommon: ConfigDataExternalFileInterfaceBase()
{
  override fun initialise (filePath: String, callingFilePath: String?)
  {
    val path = FileLocations.getInputPath(filePath, callingFilePath)
    m_XmlDocument = Dom.getDocument(path)
  }


  protected lateinit var m_XmlDocument: Document
  protected val m_XPath = XPathFactory.newInstance().newXPath()!!
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**              Code to extract data from DBL metadata files                **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
private class ConfigDataExternalFileInterfaceDbl : ConfigDataExternalFileInterfaceXmlCommon()
{
  /****************************************************************************/
  private fun getBookList (xpath: String)
  {
    /**************************************************************************/
    val myXpath: String = xpath.replace("/*", "/name")
    val bookList: MutableList<Pair<String, Map<String, String>>> = ArrayList()



    /**************************************************************************/
    val nodeList = m_XPath.compile(myXpath).evaluate(m_XmlDocument, XPathConstants.NODESET) as NodeList
    for (i in 0..< nodeList.length)
    {
      val nameNode = nodeList.item(i)
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


  /****************************************************************************/
  /* The argument (theParms) may be a list of one or more space-separated
     xpaths -- eg DBLMetadata/archiveStatus/dateUpdated  DBL/archiveStatus/dateArchived  DBLMetadata/identification/dateCompleted --
     followed optionally by an equals sign and a default value.

     The special path 'names-slash-asterisk' gives back a list of all book
     names.

     The return value is the first of the xpaths to return a value, or the
     default value if any. */

  override fun getValue (theParms: String): String?
  {
    /************************************************************************/
    //Dbg.dCont(parms, "direction")



    /************************************************************************/
    var parms = theParms.trim()
    if (parms.contains("names/*"))
    {
      getBookList(parms)
      return "dummyReturnValue"
    }



    /************************************************************************/
    /* Split out any default value. */

    var dflt: String? = null
    if ("=" in parms)
    {
      val (a, b) = parms.split("=")
      parms = a.trim()
      dflt = b.trim()
    }



    /************************************************************************/
    val paths = parms.split("\\s+".toRegex())
    var res = paths.map { getValue1(it) }. firstOrNull { null != it }



    /************************************************************************/
    return res ?: if (null == dflt) dflt else getValue(dflt)
  }


  /****************************************************************************/
  private fun getValue1 (path: String): String?
 {
    /************************************************************************/
    /* Check if we're getting an attribute. */

    var attribute: String? = null
    var res: String
    var xpath = path

    if ("/@" in path)
    {
      val x = xpath.split("/@")
      xpath = x[0]
      attribute = x[1]
    }



    /************************************************************************/
    val node = try { // Have to allow for the possibility that a given element may not exist.  In DBL, eg nameLocal may appear in a variety of locations, and we may initially have tried the wrong one.
      m_XPath.compile(xpath).evaluate(m_XmlDocument, XPathConstants.NODE) as Node
    }
    catch (_: Exception)
    {
      return null
    }

    if (null != attribute)
      return Dom.getAttribute(node, attribute)



    /************************************************************************/
    /* Obtain the full text content of the selected node. */

    res = Dom.getNodeContentAsString(node, false)

    if ("DBLMetadata/language/iso" == xpath && "eng".equals(res, ignoreCase = true)) res = "en" // Need 2-char version so Bibles are presented in the 'English Bibles' section of STEP's LoadBible screen.

    return res
  }
}
