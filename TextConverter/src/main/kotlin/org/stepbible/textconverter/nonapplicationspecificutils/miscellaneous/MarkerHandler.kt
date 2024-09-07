package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString


/******************************************************************************/
/**
 * A 'marker' is something used to flag eg footnotes.  Typically these come in
 * one of three (perhaps four) flavours: a fixed character, such as a down-
 * arrow which is used every time; a repeating set of characters (such as a,
 * b, ... z, aa, ab, ...) or an incrementing number (I guess probably in
 * vernacular format, although possibly it would be useful to use English
 * format for some parts of a text and vernacular for others, so I support
 * both here).
 *
 * @author ARA "Jamie" Jamieson
 */

object MarkerHandlerFactory
{
  /****************************************************************************/
  enum class Type { FixedCharacter, RepeatingCharacterRange, NumericVernacular, NumericEnglish }

  /****************************************************************************/
  fun createMarkerHandler (type: Type, vararg spec: String): MarkerHandler
  {
    return when (type)
    {
      Type.NumericVernacular -> MarkerHandlerNumericVernacular()
      Type.NumericEnglish-> MarkerHandlerNumericEnglish()
      Type.FixedCharacter -> MarkerHandlerFixedCharacter(*spec)
      Type.RepeatingCharacterRange -> MarkerHandlerCharacterRange(*spec)
    }
  }
}


/******************************************************************************/
interface MarkerHandler
{
  fun get () : String
  fun reset ()
}


/******************************************************************************/
class MarkerHandlerNumericEnglish: MarkerHandler
{
  override fun get (): String { return (++m_Counter).toString() }
  override fun reset () { m_Counter = 0 }
  private var m_Counter = 0
}


/******************************************************************************/
class MarkerHandlerNumericVernacular: MarkerHandler
{
  override fun get (): String { return m_Formatter.format(++m_Counter) }
  override fun reset () { m_Counter = 0 }
  private var m_Counter = 0
  private val m_Formatter = LocaleHandler.getVernacularNumberFormat()
}


/******************************************************************************/
class MarkerHandlerFixedCharacter (vararg marker: String): MarkerHandler
{
  override fun get (): String { return m_Marker }
  override fun reset () { }
  private val m_Marker: String = marker[0]
}


/******************************************************************************/
class MarkerHandlerCharacterRange (vararg range: String): MarkerHandler
{
  override fun get (): String { return convertNumberToRepeatingString(++m_Counter, m_Low, m_High) }
  override fun reset () { m_Counter = 0 }
  private var m_Counter = 0
  private val m_Low: Char = range[0][0]
  private val m_High: Char = range[1][0]
}
