/******************************************************************************/
package org.stepbible.textconverter.support.shared

import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.doNothing
import java.io.FileInputStream
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

object FeatureIdentifier
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
  
  private var m_HasFootnotes = false
  private var m_HasGlosses = false
  private var m_HasLemma = false
  private var m_HasMorphology = false
  private var m_HasMultiVerseParagraphs = false
  private var m_HasNonCanonicalHeadings = false
  private var m_HasRedLetterWords = false
  private var m_HasScriptureReferences = false
  private var m_HasStrongs = false // Looks as though you may have to include 'Feature=StrongsNumbers' in the conf file as well as setting this flag.
  private var m_HasTransliteratedForms = false
  private var m_HasVariants = false

  private var m_HasEnumeratedWords = false
  private var m_HasGlossaryLinks = false
  private var m_HasMorphologicalSegmentation = false
 }