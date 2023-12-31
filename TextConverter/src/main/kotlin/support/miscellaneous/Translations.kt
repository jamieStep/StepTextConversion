/******************************************************************************/
package org.stepbible.textconverter.support.miscellaneous

import org.stepbible.textconverter.TextConverterFeatureSummaryGenerator
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.StepStringFormatter.convertNameAndValueListToMap
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.support.stepexception.StepException


/******************************************************************************/
/**
 * Supports translations of keys to either English or vernacular text.
 *
 *
 * @author ARA "Jamie" Jamieson
 */

object Translations
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Gives back the text of a message in a given language.
  *
  * @param languageSelector The language for which the text is required
  *   ('English','Vernacular', etc).
  *
  * @param key The key using which the text is selected.
  *
  * @return The required text.
  */

  fun lookupText (languageSelector: Language, key: String): String
  {
    val res = when (languageSelector)
    {
      Language.AsIs       -> return key
      Language.Vernacular -> getVernacular(key)
      else                -> getEnglish(key)
    } ?: throw StepException("Message lookup failed on $key")

    return res
  }


  /****************************************************************************/
  /**
   * Creates a formatted string, but supports an awful lot of extensions en
   * route to that point.  For more details, see StepStringFormatter.
   *
   * @param languageSelector
   * @param key The keys for the translatable portion of the message.
   * @param otherBits Elements to be filled into string.
   *
   * @return Formatted string.
   */

  fun stringFormat (languageSelector: Language, key: String, vararg otherBits: Any): String
  {
    val text = lookupText(languageSelector, key)
    return StepStringFormatter.format(text, if (1 == otherBits.size) otherBits[0] else convertNameAndValueListToMap(*otherBits))
  }


  /****************************************************************************/
  /**
   * Creates a formatted string based upon a pre-supplied text value.
   *
   * @param text Text string.
   * @param otherBits Elements to be filled into string.
   * @return Formatted string
   */

  fun stringFormat (text: String, vararg otherBits: Any): String
  {
    return StepStringFormatter.format(text, if (1 == otherBits.size) otherBits[0] else convertNameAndValueListToMap(*otherBits))
  }


  /****************************************************************************/
  /**
   * Creates a formatted string, but supports an awful lot of extensions en
   * route to that point.  For more details, see StepStringFormatter.  The
   * basic string is obtained by looking up a key.
   *
   * @param key The keys for the translatable portion of the message.
   * @param otherBits Elements to be filled into string.
   *
   * @return Formatted string.
   */

  fun stringFormatWithLookup (key: String, vararg otherBits: Any): String
  {
    /**************************************************************************/
    var text = ConfigData[key] ?: throw StepException("stringFormatWithLookup lookup failed on $key")
    if (text.isEmpty()) return ""



    /**************************************************************************/
    /* If we're using an English text string with a non-English Bible, force
       any reference to come out in USX.  Otherwise you end up with something
       where the text itself is in English but the reference uses vernacular
       book names, and that looks odd. */

    if (ConfigData.isEnglishTranslatableText(key) && ConfigData["stepLanguageCode3Char"] !="eng")
      text = text.replace("%refV", "%refU%")



    /**************************************************************************/
    val res = StepStringFormatter.format(text, if (1 == otherBits.size) otherBits[0] else convertNameAndValueListToMap(*otherBits))
    TextConverterFeatureSummaryGenerator.addTranslatableText(key, text) // Record details of
    return res
  }


  /****************************************************************************/
  /**
   * Obtains the English text for a given key.
   *
   * @return English text, or null if not found.
   */

  private fun getEnglish (key: String): String?
  {
    return ConfigData[key]
  }


  /****************************************************************************/
  /**
   * Obtains the vernacular text for a given key, or else falls back on
   * English.
   *
   * @return Text, or null if not found.
   */

  private fun getVernacular (key: String): String?
  {
    var res = ConfigData[key]
    if (null == res) res = getEnglish(key)
    return res
  }


}
