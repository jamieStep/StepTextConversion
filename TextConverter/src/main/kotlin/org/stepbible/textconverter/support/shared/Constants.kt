/******************************************************************************/
package org.stepbible.textconverter.support.shared


/******************************************************************************/
/**
 * Basic constants to be used, for example, as identifiers.
 *
 * @author ARA "Jamie" Jamieson
 */


  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  enum class BiblePart { OT, NT, DC }


  /****************************************************************************/
  /* Kotlin vs Java :-

     - The Java version of enums supported 'public char getValue ()', which gave
       back the value of the abbreviation underlying the enum value.  In Kotlin,
       the same can be achieved using eg BookNameLength.valueOf("SHORT").

     - In the Java version, I included a constructor as well as a lookup
       function.  I believe, in fact, that the lookup function can serve both
       purposes.
  */
  
  /****************************************************************************/
  /**
   * Options for the length of book names.
   */
  
  enum class BookNameLength (private val abbrev: Char)
  {
    Abbreviated('-'), Short('='), Long('+'), Undefined('?');   
    
    fun lookup (c: Char): BookNameLength?
    {
      populateMap()
      return m_Map[c]
    }
    
    private fun populateMap () {
      if (m_Map.isEmpty())
        for (x in BookNameLength.values())
          m_Map[x.abbrev] = BookNameLength.valueOf(x.name)
    }
    
    private val m_Map: MutableMap<Char, BookNameLength> = mutableMapOf()
  }

  
  /****************************************************************************/
  /**
   * Language selectors.
   */

  enum class Language {  English, Vernacular, AsIs }


  /****************************************************************************/
  /**
   * Possible representation schemes.
   */
  
  enum class RepresentationScheme (private val abbrev: Char)
  {
    OSIS('o'), USX('u'), Vernacular('x'), Undefined('?');
  
    fun lookup (c: Char): RepresentationScheme?
    {
      populateMap()
      return m_Map[c]
    }
    
    private fun populateMap ()
    {
      if (m_Map.isEmpty())
        for (x in RepresentationScheme.values())
          m_Map[x.abbrev] = RepresentationScheme.valueOf(x.name)
    }
    
    private val m_Map: MutableMap<Char, RepresentationScheme> = mutableMapOf()
  }

