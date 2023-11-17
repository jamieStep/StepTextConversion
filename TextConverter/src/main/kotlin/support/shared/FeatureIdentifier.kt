/******************************************************************************/
package org.stepbible.textconverter.support.shared

import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.doNothing
import java.io.FileInputStream
import java.util.TreeMap
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader



/******************************************************************************/
/**
 * Reads the OSIS file and extracts those features which we have to identify in
 * the Sword config file.
 * 
 * @author ARA "Jamie" Jamieson
 */

class FeatureIdentifier
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Records the fact that we have some special feature.
   * 
   * @param name Name for feature.
   * @param value Associated value.
   */
  
  fun addSpecialFeature (name: String, value: String)
  {
    m_SpecialFeatures[name] = value
  }
  
  
  /****************************************************************************/
  /**
   * Gets details of any special features.
   * 
   * @return Special features.
   */
  
  fun getSpecialFeatures (): TreeMap<String, String>
  {
    return m_SpecialFeatures
  }
  
  
  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasEnumeratedWords (): Boolean { return m_HasEnumeratedWords; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasFootnotes (): Boolean { return m_HasFootnotes; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasGlossaryLinks (): Boolean { return m_HasGlossaryLinks; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasGlosses (): Boolean { return m_HasGlosses; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasLemma (): Boolean { return m_HasLemma; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasMorphologicalSegmentation (): Boolean { return m_HasMorphologicalSegmentation; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasMorphology (): Boolean { return m_HasMorphology; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasMultiVerseParagraphs (): Boolean { return m_HasMultiVerseParagraphs; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasNonCanonicalHeadings (): Boolean { return m_HasNonCanonicalHeadings; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasRedLetterWords (): Boolean { return m_HasRedLetterWords; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasScriptureReferences (): Boolean { return m_HasScriptureReferences; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasStrongs (): Boolean { return m_HasStrongs; }


  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasTransliteratedForms (): Boolean { return m_HasTransliteratedForms; }


  /****************************************************************************/
  /**
   * Flags that we have multi verse paragraphs.
   */
   
  fun setHasMultiVerseParagraphs () { m_HasMultiVerseParagraphs = true; }


  /****************************************************************************/
  /**
   * Records the fact that we have Strongs.  Most of the flags here are set as a
   * result of parsing the OSIS (and in fact there's code to set the Strongs
   * flag too), but we need to know about Strongs before we get as far as
   * generating the OSIS, since the OSIS header is determined in part by
   * whether we have Strongs.
   */
  
  fun setStrongs ()
  {
    m_HasStrongs = true
  }
  
  
  /****************************************************************************/
  /**
   * Returns the value indicated by the name of the method.
   *
   * @return Value indicated by the name of the method.
   */
   
  fun hasVariants (): Boolean { return m_HasVariants; }


  /****************************************************************************/
  /**
   * Parses the OSIS file in order to detect features which needs to be
   * flagged in the Sword config file.
   * 
   * @param osisFilePath The full path name of the OSIS file.
   * @throws Exception Anything the underling code cares to throw.
   */
  
  fun process (osisFilePath: String)
  {
    val xmlInputFactory = XMLInputFactory.newInstance()
    xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true)
    val xmlStreamReader = xmlInputFactory.createXMLStreamReader(FileInputStream(osisFilePath))
    while (xmlStreamReader.hasNext())
    {
      processElement(xmlStreamReader)
      xmlStreamReader.next() // Note the unusual placement of next.
    }
  }
  
  
  /****************************************************************************/
  /**
   * Does what it says on the tin.
   * 
   * @param v New setting.
   */
  
  fun setHasFootnotes (v: Boolean)
  {
    m_HasFootnotes = v
  }




  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun getAttribute (reader: XMLStreamReader, attribute: String): String?
  {
    var res: String? = null
    for (i in 0..< reader.attributeCount)
      if (attribute == reader.getAttributeLocalName(i))
      {
        res = reader.getAttributeValue(i)
        break
      }
    
    return res
  }
  
  
  /****************************************************************************/
  private fun processElement (reader: XMLStreamReader)
  {
    /**************************************************************************/
    when (reader.eventType)
    {
      XMLStreamConstants.START_DOCUMENT,
      XMLStreamConstants.END_DOCUMENT,
      XMLStreamConstants.SPACE,
      XMLStreamConstants.CHARACTERS,
      XMLStreamConstants.COMMENT ->
        return
      
      
      XMLStreamConstants.START_ELEMENT ->
        processStartElement(reader)

      
      XMLStreamConstants.END_ELEMENT ->
        return // processEndElement(reader)
    }
  }
    
 
  /****************************************************************************/
  private fun processEndElement (reader: XMLStreamReader)
  {
    doNothing(reader)
  }
  
  
  /****************************************************************************/
  private fun processStartElement (reader: XMLStreamReader)
  {
    /**************************************************************************/
    //Dbg.d(reader.getLocalName())
    
    
    
    /**************************************************************************/
    when (reader.localName)
    {
      /************************************************************************/
      /* I'm not actually sure this caters for all possibilities. */
      
      "note" ->
      {
        if (!m_HasFootnotes || !m_HasScriptureReferences || !m_HasVariants)
        {
          val s = getAttribute(reader, "type")!!.substring(0, 3).lowercase()

          if (!m_HasScriptureReferences && "cro" == s) // crossReference
            m_HasScriptureReferences = true
          else if (!m_HasVariants && "var" == s)
            m_HasVariants = true
          else
            m_HasFootnotes = true
        }
      } // note
      
      
      /************************************************************************/
      "q" ->
      {
        if (!m_HasRedLetterWords)
        {
          val s = getAttribute(reader, "who") ?: ""
          if ("Jesus".equals(s, ignoreCase = true))
            m_HasRedLetterWords = true
        }
      } // q

      
      /************************************************************************/
      "title" ->
      {
        if (!m_HasNonCanonicalHeadings)
        {
          val s = getAttribute(reader, "canonical")
          m_HasNonCanonicalHeadings = (null == s || "false".equals(s, ignoreCase = true))
        }
      } // title
      
      
      /************************************************************************/
      "w" ->
      {
        if (!m_HasGlosses)             m_HasGlosses             = null != getAttribute(reader, "gloss")
        if (!m_HasLemma)               m_HasLemma               = null != getAttribute(reader, "lemma")
        if (!m_HasMorphology)          m_HasMorphology          = null != getAttribute(reader, "morph")
        if (!m_HasTransliteratedForms) m_HasTransliteratedForms = null != getAttribute(reader, "xlit")
        
        if (!m_HasStrongs)
        {
          val s = getAttribute(reader, "gloss")
          m_HasStrongs = null != s && (s.matches(Regex(".*H\\d+.*")) || s.matches(Regex(".*G\\d+.*")))
        }
      } // w
      
    } // when
  }
  
  
  /****************************************************************************/
  /* Many of the following are used to set flags in the Sword config file.  In
     some (all) cases, the flags have to be set appropriately in order to
     enable relevant features in the displayed text -- so, for example, if your
     text contains non-canonical headings, but you omit to tell Sword as much,
     non-canonical headings are not displayed.
  
     I am assuming that most or all of these can be deduced from the actual
     content of the text being processed, but have not necessarily found a way
     to do so yet in all cases (or perhaps have not yet encountered texts in
     which this was an issue).  And indeed, some of them I understand so little
     that I have no idea what they mean (and therefore fairly obviously no idea
     of how to recognise whether a given text requires them).
  
     Incidentally, I have no idea of the implications of simply telling Sword
     that they are all relevant even if, on a given text, perhaps they are not.
     Possibly to do this would be satisfactory, and would save agonising over
     them.
  
     I have split the items into two blocks.  Ones marked final and with a value
     of false are the ones I am not presently setting by reference to the text
     being processed. */
  
  private var m_HasFootnotes:Boolean = false
  private var m_HasGlosses:Boolean = false
  private var m_HasLemma:Boolean = false
  private var m_HasMorphology:Boolean = false
  private var m_HasMultiVerseParagraphs:Boolean = false
  private var m_HasNonCanonicalHeadings:Boolean = false
  private var m_HasRedLetterWords:Boolean = false
  private var m_HasScriptureReferences:Boolean = false
  private var m_HasStrongs:Boolean = false // Looks as though you may have to include 'Feature=StrongsNumbers' in the conf file as well as setting this flag.
  private var m_HasTransliteratedForms:Boolean = false
  private var m_HasVariants:Boolean = false

  private var m_HasEnumeratedWords:Boolean = false
  private var m_HasGlossaryLinks:Boolean = false
  private var m_HasMorphologicalSegmentation:Boolean = false
  
  private val m_SpecialFeatures = TreeMap<String, String>()
 }