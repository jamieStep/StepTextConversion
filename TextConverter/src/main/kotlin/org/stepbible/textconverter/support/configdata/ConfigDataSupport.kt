/****************************************************************************/
package org.stepbible.textconverter.support.configdata

import org.stepbible.textconverter.support.iso.IsoLanguageCodes


/****************************************************************************/
/**
 * Stuff to do with Sword modules, configuration files, etc.
 *
 * @author ARA "Jamie" Jamieson
 */

object ConfigDataSupport
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * This is a complicated method ...
   *
   * I have adopted a convention whereby the root folder for texts has a name
   * comprised of the ISO code for the language (2- or 3- character), depending
   * upon what has been given to us in the metadata, all lower case, followed
   * by an underscore and then an abbreviation or very short full name for the
   * text being handled, case immaterial.
   *
   * This has the advantage of being easy to parse and, if we were to use it for
   * our module names, of being distinctive (at least in respect of the names of
   * modules from other sources which I've seen at the time of writing).
   *
   * I wish to retain this convention for folder names, because certain aspects
   * of the processing rely upon it, and it is a relatively easy one to use when
   * creating folders for new texts (ie you don't have to think about it too much).
   *
   * However, I have been asked to adopt a different convention for actual
   * module names :-
   *
   * - 3-character ISO codes only.
   * - ISO code to be sentence case.
   * - No underscore.
   * - Abbreviation to start with capital.
   *
   * This is, to my mind, unfortunate.  It has not been particularly easy to
   * implement, because previously the module name was also used for path names
   * generally, and I now have to distinguish between path names and module
   * names.  It is no longer distinctive, so we cannot recognise our own
   * modules simply by reference to the file name.  And we cannot reliably
   * extract language codes from file names because modules from other sources
   * use the same formatting, but use it to convey different semantics.
   *
   *
   * Note that most callers can rely upon this being sorted out automatically
   * when they initialise the metadata.  However, anything which is
   * *generating* configuration data will need to make an explicit call to
   * it.
   *
   *
   * Note further that the module name may need to have a suffix added to it, to
   * flag situations where we have added significant value.  I can't make that
   * call until after reversification has occurred, so at this point I simply
   * create stepModuleNameWithoutSuffix.  Later processing will have to
   * sort things out.
   *
   * Except that on 13-Apr-22, DIB asked for this additional suffix to be
   * suppressed in many cases -- something I now control via the parameter
   * stepDecorateModuleNamesWhereStepHasAddedValue.
   */

  fun determineModuleDetails ()
  {
    /**************************************************************************/
    val rootName = StandardFileLocations.getRootFolderName().replace("%Text_", "")
    var languageCode: String
    var abbreviatedName: String



    /**************************************************************************/
    if (rootName.contains("_"))
    {
      val x = rootName.split("_")
      languageCode = x[0].lowercase()
      abbreviatedName = x[1]
    }
    else
    {
      languageCode = ""
      abbreviatedName = rootName
    }



    /**************************************************************************/
    /* Language codes -- force to three characters, except that if the code is
       English, we omit it. */

    if (2 == languageCode.length) languageCode = IsoLanguageCodes.get3CharacterIsoCode(languageCode)
    ConfigData.put("stepLanguageCode3Char", languageCode, true)
    ConfigData.put("stepLanguageNameInEnglish", IsoLanguageCodes.getLanguageName(languageCode), true)
    ConfigData.put("stepLanguageCode2Char", IsoLanguageCodes.get2CharacterIsoCode(languageCode), true)
    var sentenceCaseLanguageCode = languageCode.lowercase().replaceFirstChar{ it.uppercase() }
    ConfigData.put("stepLanguageCode3CharSentenceCase", sentenceCaseLanguageCode, true)



    /**************************************************************************/
    /* Sort out case. */

    abbreviatedName = abbreviatedName.substring(0, 1).uppercase() + abbreviatedName.substring(1)
    ConfigData.put("stepAbbreviatedTextNameCanonical", abbreviatedName, true)



    /**************************************************************************/
    if ("eng" == languageCode || "grc" == languageCode) sentenceCaseLanguageCode = ""
    ConfigData.put("stepModuleNameWithoutSuffix", sentenceCaseLanguageCode + abbreviatedName, true)
  }
}