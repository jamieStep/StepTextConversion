package org.stepbible.textconverter.support.iso

import org.stepbible.textconverter.support.configdata.FileLocations


/******************************************************************************/
/**
 * Information related to ISO language codes and countries.
 *
 *
 *
 * ### Background
 *
 * Historically, language codes are defined by the ISO 639-series.  The
 * data in this present file is based on ISO 639-2, which is the latest version
 * for which full information is available free of charge on the internet++ --
 * see [ISO 639-2](https://www.loc.gov/standards/iso639-2/ascii_8bits.html).
 * There are later standards ISO 639-3 and ISO 639-5 (639-4 apparently did
 * not change the codes, and 639-6 was dropped).  It is claimed that 639-3
 * and that 639-5 is ... well, even more complete.
 *
 * ++ Looking back through my notes, it appears that at some point I did
 * actually track down 639-3, but decided against using it, because it ran
 * to over 8000 entries.
 *
 * All languages have a 3-character language code.  A very few have two
 * different 3-character codes.  And a fair number also have a 2-character
 * code.
 *
 * The 2-character codes represent an earlier attempt at classification, and
 * given the more limited options available in two characters, are able to
 * represent fewer languages.  Hence the need for 3-character codes.
 *
 * And for those few languages which have two 3-character codes, one of the
 * codes (the so-called 'bibliographic' code) is historical in nature, and
 * the other ('terminologic') is newer.  According to various non-official
 * websites, the bibliographic code *may* be used for certain purposes, but
 * the terminologic code is to be preferred.  (The latter is always based
 * upon the vernacular name of the language, romanised where necessary;
 * the former upon the English name for the language.)
 *
 * In addition to the language codes described above, some languages also have
 * 'sub-codes', reflecting the fact that a local variant of the language
 * exists.  For example, Canadian French may be distinguished from 'French
 * French' as fr-CAN.  I *believe* this level of refinement mainly covers
 * things such as different time and date formats in different places and is
 * something which (at present) we do not worry about.
 *
 * In fact, though, this information appears now to have been superseded.
 * According to the discussion [here](https://www.w3.org/International/questions/qa-lang-2or3.en.html)
 * rather than make some rule-driven choice based upon the ISO standard
 * documents, you should visit the [registry](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry)
 * and simply go with whatever appears there.
 *
 * Unfortunately, this information in turn is irrelevant, because we are
 * now trying to follow the practice adopted by Crosswire, and this doesn't
 * appear to follow the standard.
 *
 * I can best summarise the situation by giving an example.
 *
 * We have access to Hoffnung fuer Alle from Biblica, via DBL.
 *
 * This is in German.  According to the IANA registry, the code to be used
 * here is 'de'.
 *
 * However, the DBL metadata gives the language code as 'deu'.  My previous
 * practice has been to go with this, on the grounds a) that it is what the
 * translators specified; and b) that it is the simplest option, because I
 * can take it direct from the metadata file.  To do that *would* mean ignoring
 * the current standard, but I haven't worried about that.
 *
 * However, German is one of those languages which has *two* 3-character codes,
 * and Crosswire's practice is to use 'ger' rather than 'deu' on German texts.
 * This, of course, conforms neither to the standard, nor to previously
 * recommended practice, nor to what the translators have specified, but it is
 * what it is.  So we use 'ger'.
 *
 *
 *
 *
 * ### Identifying the language code
 *
 * The processing picks up the language code from the name of the root folder
 * for the text.  Root folder names are of the form Text_xxxxx_Abbrev where
 * xxxx is the language code and Abbrev is the abbreviated name of the text.
 * Thus we might have, for instance, Text_eng_NIV.  The language code must
 * always be supplied here, even though the code is dropped from the module
 * names of English modules and modules based upon biblical Hebrew or Greek.
 *
 * The language code is not case-sensitive, and hitherto I have not worried
 * as to whether it is a 2- or 3- character code: things have been converted
 * to a suitable canonical form during processing.  I am not sure whether I
 * can continue to do this in future, because I gather Crosswire are not
 * consistent when it comes to naming French modules, so I may have to require
 * that the folder name contain the actual code required.
 *
 *
 *
 *
 *
 * ### Use of the language code, and impact of possible changes
 *
 * The code is used in module names.  This processing should continue to work
 * irrespective of any changes to the way the codes are determined in future.
 *
 * It also appears in various file names, most notably those which contain
 * translations of the text used in footnotes.  Here it is used to make it
 * possible to pick up the data relevant to the given language automatically,
 * and changes to the way the language codes are determined *will* have an
 * impact -- it will be necessary to track down all of the files affected and
 * alter their names to reflect the code now adopted.
 *
 * I don't *think* there is any impact beyond this, but cannot guarantee it.
 *
 * Or there won't be any other impact upon the converter.  However, anything
 * which affects module names may have an impact upon the STEP repository, and
 * should therefore be run past Darcy.
 *
 * @author ARA "Jamie" Jamieson
 */

object IsoLanguageAndCountryCodes {
    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Public                                  **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
     * Given a language code, returns the equivalent two character code.  The
     * parameter may be a 2- or 3- character code, and in the latter case may be
     * either the bibliographic or the terminologic code where both are defined.
     *
     * @param isoCode What it says on the tin.
     * @return Two character code.
     */

    fun get2CharacterIsoCode (isoCode: String): String
    {
      val asSupplied = isoCode.lowercase()
      if (2 == isoCode.length) return isoCode.lowercase()
      return m_3CharToLanguageDetails[asSupplied]!!.first ?: asSupplied
    }


    /****************************************************************************/
    /**
     * Given a language code, returns the preferred three character code.  The
     * parameter may be a 2- or 3- character code, and in the latter case may be
     * either the bibliographic or the terminologic code where both are defined.
     *
     * @param isoCode What it says on the tin.
     * @return Three character code.
     * */

    fun get3CharacterIsoCode (isoCode: String): String
    {
      val asSupplied = isoCode.lowercase()
      return if (2 == asSupplied.length) m_2CharTo3Char[asSupplied]!! else asSupplied
    }


    /****************************************************************************/
    /**
    * Given a 3-char ISO language code, returns the list of countries where that
    * language is used, or null if no details are found
    *
    * @param code3Char
    * @return List of countries where the language is used.
    */

    fun getCountriesWhereUsed (code3Char: String) = m_3CharToCountriesWhereUsed[code3Char]


    /****************************************************************************/
    /**
     * Given a language code, returns the corresponding language name.  The code
     * may be 2- or 3- char, and in the latter may be the default name or our
     * preferred one where both are defined (ie in ISO terms may be either the
     * bibliographic or the terminologic code).
     *
     * @param isoCode What it says on the tin.
     * @return Language name.
     */

    fun getLanguageName (isoCode: String): String
    {
      val asSupplied = isoCode.lowercase()
      val key = if (2 == isoCode.length) m_2CharTo3Char[asSupplied] else asSupplied
      return m_3CharToLanguageDetails[key]!!.second
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Private                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

  /****************************************************************************/
  private val m_2CharTo3Char: MutableMap<String, String> = mutableMapOf()
  private val m_3CharToCountriesWhereUsed: MutableMap<String, String> = mutableMapOf()
  private val m_3CharToLanguageDetails: MutableMap<String, Pair<String?, String>> = mutableMapOf()


  /****************************************************************************/
  init
  {
    /**************************************************************************/
    val countryNameMappings: MutableMap<String, String> = mutableMapOf()
    FileLocations.getInputStream(FileLocations.getCountryCodeInfoFilePath())!!.bufferedReader().lines().forEach {
      val line = it.trim()
      if (line.isEmpty() || line.startsWith("#!"))
        return@forEach

      val (longForm, shortForm) = (line + "\t").split("\t")
      countryNameMappings[longForm] = shortForm
    }


    /**************************************************************************/
    /* 3char language code to combination (2char code + list of countries where
       used.  I used shortened country names where these are defined. */
       
    FileLocations.getInputStream(FileLocations.getIsoLanguageCodesFilePath())!!.bufferedReader().lines().forEach {
      val line = it.trim()
      if (line.isEmpty() || line.startsWith("#!"))
        return@forEach

      val (code3Char, code2Char, languageName, countriesWhereUsed) = (line + "\t").split("\t")
      val revisedLanguageName = countryNameMappings[languageName] ?: languageName
      if (code2Char.isNotEmpty()) m_2CharTo3Char[code2Char] = code3Char
      m_3CharToLanguageDetails[code3Char] = Pair(code2Char, revisedLanguageName)
      m_3CharToCountriesWhereUsed[code3Char] = countriesWhereUsed
    }
  }
}