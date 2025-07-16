package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringFormatter
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringFormatter.convertNameAndValueListToMap
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.stepbible.textconverter.applicationspecificutils.IssueAndInformationRecorder
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun

/******************************************************************************/
/**
 * Handles translatable fixed text -- things like the text which goes into
 * reversification footnotes, and which might usefully be translated into the
 * vernacular.
 *
 * This cannot be initialised until after ConfigData has been loaded.
 *
 * After that you can use *getEnglish* or *getVernacular* to obtain either the
 * English or the vernacular text associated with a given key.  You can also
 * use the shorthand TranslatableFixedText\[key\] to get the vernacular text.
 *
 * As regards things which might go wrong ...
 *
 * - If you ask for an English text and the given key is not available, the
 *   code throws an exception.
 *
 * - If you ask for a vernacular text and the given key does not feature in the
 *   vernacular list, you instead get the English text if available.  However,
 *   this situation indicates a possible drop-off -- perhaps you have forgotten
 *   to obtain translations for this vernacular, or perhaps the translations you
 *   do have are incomplete, so a warning is issued.  If the English text is
 *   required but is not available, then the code throws an exception.
 *
 * - If you ask for a vernacular text, and the value associated with that key
 *   is #Untranslateble#, this indicates that you have made an attempt to obtain
 *   a translation, but Google Translate has failed to supply one (perhaps
 *   because this is a language it does not presently cater for).  In this
 *   case the English text is returned, and no warning is issued.
 *
 *
 *
 * PLEASE READ ME ...
 *
 * One thing you might perhaps want to be aware of (but will inevitably forget).
 * We are dealing here with texts which may be translated into non-English
 * languages.  The basic text for each message is in English; and it may contain
 * place-holders to be filled in with scripture references etc.  These place-
 * holders contain Latin characters, and must remain unchanged in the translated
 * form of the message.  When you are dealing with RTL languages, such as
 * Arabic, and have Latin characters mixed in, the result can look odd, and
 * things can appear to be in the wrong order.  However, I *think* (hope) that
 * once the placeholders are themselves replaced with the RTL text which they
 * are intended to display, things seem to come out ok.  In other words, matters
 * can be extremely confusing during development work, but the end result still
 * looks ok.  Hopefully.

 *
 * @author ARA "Jamie" Jamieson
 */

object TranslatableFixedText: ObjectInterface
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
    var res = when (languageSelector)
    {
      Language.AsIs       -> return key
      Language.Vernacular -> getVernacular(key).second
      else                -> getEnglish(key)
    }

    if ("#Untranslatable#" == res)
      res = getEnglish(key)

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
    return StepStringFormatter.format(text, *otherBits) //convertNameAndValueListToMap(*otherBits))
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
    var (type, text) = getVernacular(key)
    val isEnglish = 'E' == type
    //text = text.substring(2)
    if (text.isEmpty()) return ""



    /**************************************************************************/
    /* If we're using an English text string with a non-English Bible, force
       any reference to come out in USX.  Otherwise you end up with something
       where the text itself is in English but the reference uses vernacular
       book names, and that looks odd. */

    if (isEnglish && m_VernacularCode !="eng")
      text = text.replace("%refV", "%refU")



    /**************************************************************************/
    val res = StepStringFormatter.format(text, if (1 == otherBits.size) otherBits[0] else convertNameAndValueListToMap(*otherBits))
    IssueAndInformationRecorder.addTranslatableTextWhichWasInEnglishWhenVernacularWouldBeBetter(key, text) // Record details of which translation keys are used.
    return res
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

  fun stringFormatWithLookupEnglish (key: String, vararg otherBits: Any): String
  {
    /**************************************************************************/
    /* Force references to come out in English, not in the vernacular --
       vernacular references in an English text will look odd. */

    var text = m_English[key]!!.replace("%refV", "%refU")
    if (text.isEmpty()) return ""



    /**************************************************************************/
    val res = StepStringFormatter.format(text, if (1 == otherBits.size) otherBits[0] else convertNameAndValueListToMap(*otherBits))
    IssueAndInformationRecorder.addTranslatableTextWhichWasInEnglishWhenVernacularWouldBeBetter(key, text) // Record details of which translation keys are used.
    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val m_English   : MutableMap<String, String> = mutableMapOf()
  private val m_Vernacular: MutableMap<String, String> = mutableMapOf() // Contains definitions for the vernacular specific to this run only.  Does not get populated if the vernacular is eng.
  private val m_VernacularCode: String = ConfigData["calcLanguageCode3Char"] ?: throw StepExceptionWithStackTraceAbandonRun("Initialising TranslatableFixedText too early.") // The language code for the text being processed.
  private val m_WarnedAboutUseOfEnglishTranslations: MutableSet<String> = mutableSetOf() // Used to prevent duplication of warnings.


  /****************************************************************************/
  /*
  * Returns the English text associated with a given key.  Throws an exception
  * if the key does not have any associated text.
  */

  private fun getEnglish (key: String) = m_English[key] ?: throw StepExceptionWithStackTraceAbandonRun("Failed to find English message associated with '$key'.")


  /****************************************************************************/
  /*
  * Returns the vernacular text associated with a given key.  Throws an exception
  * if the key does not have any associated text.
  */

  private fun getVernacular (key: String): Pair<Char, String>
  {
     val res = if ("eng" == m_VernacularCode) m_English[key] else m_Vernacular[key]
     when (res)
     {
       null ->
       {
         if (key !in m_WarnedAboutUseOfEnglishTranslations)
         {
           Logger.warning("No vernacular entry for key $key.")
           m_WarnedAboutUseOfEnglishTranslations.add(key)
         }

         return Pair('E', getEnglish(key))
       }


       "#Untranslatable#" ->
         return Pair('E', getEnglish(key))


       else ->
         return Pair('V', res)
     }
  }


  /****************************************************************************/
  /* Processes a 'standard' definition of the form <lang>|<key>|<text>. */

  private fun processDefinition (line: String)
  {
    val x = line.split("|")
    if ("eng" == x[0])
      m_English[x[1]] = x[2]
    else
      m_Vernacular[x[1]] = x[2].replace("% ref", "%ref")
  }


  /****************************************************************************/
  /* This handles 'special' definitions, which look something like this:

       Special:V_contentForEmptyVerse_verseAddedByReversification=&#x2013; #! Some comment.

     where the '#! Some comment.' is optional.

     These are used to hold definitions which cannot be automatically
     translated by Google Translate.

     In the example above, we are using an en-dash (&#x2013;) as the content of
     an empty verse which we fabricate as part of the reversification
     processing.  It is conceivable that this may be inappropriate in some
     non-Latin language, in which case we need to override it.  However, Google
     Translate is not going to be able to come up with a meaningful alternative.
     So for these, the above definition gives a default, but it can be overridden
     by specifying a value for V_contentForEmptyVerse_verseAddedByReversification
     as part of the standard configuration data.

     The value here is stored the English version.  It is also stored as the
     vernacular value, unless the configuration data gives a different definition,
     in which case, that is used.
   */

   private fun processDefinitionSpecial (line: String)
  {
    var (key, value) = line.replace("Special:", "").trim().split("=")
    value = value.split("#!")[0].trim() // Remove any comment.
    m_English[key] = value
    m_Vernacular[key] = ConfigData[key] ?: value // If the config data has a value, that overrides.
  }


  /****************************************************************************/
  init
  {
    val code3Char = "(eng|$m_VernacularCode)"
    val C_DefinitionPattern = "^$code3Char\\|.+".toRegex()

    FileLocations.getInputStream(FileLocations.getVernacularTextDatabaseFilePath()).first!!.bufferedReader().lines().forEach {
      val line = it.trim()
      if (line.startsWith("Special:"))
        processDefinitionSpecial(line)
      else if (line.matches(C_DefinitionPattern))
        processDefinition(line)
    }
  }
}
