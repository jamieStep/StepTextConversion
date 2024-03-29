package org.stepbible.textconverter.support.iso

import org.stepbible.textconverter.support.debug.Dbg


/******************************************************************************/
/**
 * Information related to ISO language codes.
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

object IsoLanguageCodes {
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
      if (2 == isoCode.length) return isoCode.lowercase()
      val key = m_3CharToPreferred3Char[isoCode.lowercase()]
      return m_Preferred3CharToDetails[key]!!.first!!
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

    fun get3PreferredCharacterIsoCode (isoCode: String): String
    {
        val languageCode = isoCode.lowercase()
        return if (2 == languageCode.length) m_2CharToPreferred3Char[isoCode]!! else m_3CharToPreferred3Char[isoCode]!!
    }


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
      val key = if (2 == isoCode.length) m_2CharToPreferred3Char[isoCode.lowercase()] else m_3CharToPreferred3Char[isoCode.lowercase()]
      return m_Preferred3CharToDetails[key]!!.second
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Private                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /* Delete me ... */
//    private data class Length2AndName (val twoCharacterCode: String, val name: String)
//    private val m_2To3: MutableMap<String, String> = HashMap()
//    private val m_3To2: Map<String, Length2AndName> = mapOf (
//        "aar" to Length2AndName("aa", "Afar"),
//        "abk" to Length2AndName("ab", "Abkhazian"),
//        "afr" to Length2AndName("af", "Afrikaans"),
//        "aka" to Length2AndName("ak", "Akan"),
//        "alb" to Length2AndName("sq", "Albanian"),
//        "amh" to Length2AndName("am", "Amharic"),
//        "ara" to Length2AndName("ar", "Arabic"),
//        "arb" to Length2AndName("ar", "Standard Arabic"),
//        "arg" to Length2AndName("an", "Aragonese"),
//        "arm" to Length2AndName("hy", "Armenian"),
//        "asm" to Length2AndName("as", "Assamese"),
//        "ava" to Length2AndName("av", "Avaric"),
//        "ave" to Length2AndName("ae", "Avestan"),
//        "aym" to Length2AndName("ay", "Aymara"),
//        "aze" to Length2AndName("az", "Azerbaijani"),
//        "bak" to Length2AndName("ba", "Bashkir"),
//        "bam" to Length2AndName("bm", "Bambara"),
//        "baq" to Length2AndName("eu", "Basque"),
//        "bel" to Length2AndName("be", "Belarusian"),
//        "ben" to Length2AndName("bn", "Bengali"),
//        "bih" to Length2AndName("bh", "Bihari languages"),
//        "bis" to Length2AndName("bi", "Bislama"),
//        "bos" to Length2AndName("bs", "Bosnian"),
//        "bre" to Length2AndName("br", "Breton"),
//        "bul" to Length2AndName("bg", "Bulgarian"),
//        "bur" to Length2AndName("my", "Burmese"),
//        "cat" to Length2AndName("ca", "Catalan, Valencian"),
//        "ceb" to Length2AndName("ceb", "Cebuano"),
//        "ces" to Length2AndName("cs", "Czech"),
//        "cha" to Length2AndName("ch", "Chamorro"),
//        "che" to Length2AndName("ce", "Chechen"), // No 2-char code.
//        "chi" to Length2AndName("zh", "Chinese"),
//        "chu" to Length2AndName("cu", "Church Slavic, Church Slavonic, Old Bulgarian, Old Church Slavonic, Old Slavonic"),
//        "chv" to Length2AndName("cv", "Chuvash"),
//        "ckb" to Length2AndName("ckb", "Central Kurdish"), // No 2-char code.
//        "cmn" to Length2AndName("zh", "Mandarin Chinese"), // cmn is Mandarin, which I presume translates to zh.  However, we've only encountered it in one of the Biblica texts, and there I've been asked to replace cmn by chi.
//        "cor" to Length2AndName("kw", "Cornish"),
//        "cos" to Length2AndName("co", "Corsican"),
//        "cre" to Length2AndName("cr", "Cree"),
//        "dan" to Length2AndName("da", "Danish"),
//        "deu" to Length2AndName("de", "German"),
//        "div" to Length2AndName("dv", "Dhivehi, Divehi, Maldivian"),
//        "dzo" to Length2AndName("dz", "Dzongkha"),
//        "eng" to Length2AndName("en", "English"),
//        "epo" to Length2AndName("eo", "Esperanto"),
//        "est" to Length2AndName("et", "Estonian"),
//        "ewe" to Length2AndName("ee", "Ewe"),
//        "fao" to Length2AndName("fo", "Faroese"),
//        "fij" to Length2AndName("fj", "Fijian"),
//        "fin" to Length2AndName("fi", "Finnish"),
//        "fra" to Length2AndName("fr", "French"),
//        "fry" to Length2AndName("fy", "Western Frisian"),
//        "ful" to Length2AndName("ff", "Fulah"),
//        "gbr" to Length2AndName("gbr", "Gbagyi"), // No 2-char code.
//        "geo" to Length2AndName("ka", "Georgian"),
//        "gla" to Length2AndName("gd", "Gaelic, Scottish Gaelic"),
//        "gle" to Length2AndName("ga", "Irish"),
//        "glg" to Length2AndName("gl", "Galician"),
//        "glv" to Length2AndName("gv", "Manx"),
//        "grc" to Length2AndName("grc", "Ancient Greek to 1453"),
//        "gre" to Length2AndName("el", "Modern Greek (1453-)"),
//        "grn" to Length2AndName("gn", "Guarani"),
//        "guj" to Length2AndName("gu", "Gujarati"),
//        "hat" to Length2AndName("ht", "Haitian, Haitian Creole"),
//        "hau" to Length2AndName("ha", "Hausa"),
//        "hbs" to Length2AndName("sh", "Serbo-Croatian"),
//        "heb" to Length2AndName("he", "Hebrew"),
//        "her" to Length2AndName("hz", "Herero"),
//        "hil" to Length2AndName("hil", "Hiligaynon"), // No 2-char code.
//        "hin" to Length2AndName("hi", "Hindi"),
//        "hmo" to Length2AndName("ho", "Hiri Motu"),
//        "hne" to Length2AndName("hne", "Chhattisgarhi"), // No 2-char code.
//        "hrv" to Length2AndName("hr", "Croatian"),
//        "hun" to Length2AndName("hu", "Hungarian"),
//        "ibo" to Length2AndName("ig", "Igbo"),
//        "ice" to Length2AndName("is", "Icelandic"),
//        "ido" to Length2AndName("io", "Ido"),
//        "iii" to Length2AndName("ii", "Nuosu, Sichuan Yi"),
//        "iku" to Length2AndName("iu", "Inuktitut"),
//        "ile" to Length2AndName("ie", "Interlingue, Occidental"),
//        "ina" to Length2AndName("ia", "Interlingua (International Auxiliary Language Association)"),
//        "ind" to Length2AndName("id", "Indonesian"),
//        "ipk" to Length2AndName("ik", "Inupiaq"),
//        "ita" to Length2AndName("it", "Italian"),
//        "jav" to Length2AndName("jv", "Javanese"),
//        "jpn" to Length2AndName("ja", "Japanese"),
//        "kal" to Length2AndName("kl", "Greenlandic, Kalaallisut"),
//        "kan" to Length2AndName("kn", "Kannada"),
//        "kas" to Length2AndName("ks", "Kashmiri"),
//        "kau" to Length2AndName("kr", "Kanuri"),
//        "kaz" to Length2AndName("kk", "Kazakh"),
//        "khm" to Length2AndName("km", "Central Khmer, Khmer"),
//        "kik" to Length2AndName("ki", "Gikuyu, Kikuyu"),
//        "kin" to Length2AndName("rw", "Kinyarwanda"),
//        "kir" to Length2AndName("ky", "Kirghiz, Kyrgyz"),
//        "kom" to Length2AndName("kv", "Komi"),
//        "kon" to Length2AndName("kg", "Kongo"),
//        "kor" to Length2AndName("ko", "Korean"),
//        "kua" to Length2AndName("kj", "Kuanyama, Kwanyama"),
//        "kur" to Length2AndName("ku", "Kurdish"),
//        "lao" to Length2AndName("lo", "Lao"),
//        "lat" to Length2AndName("la", "Latin"),
//        "lav" to Length2AndName("lv", "Latvian"),
//        "lim" to Length2AndName("li", "Limburgan, Limburger, Limburgish"),
//        "lin" to Length2AndName("ln", "Lingala"),
//        "lit" to Length2AndName("lt", "Lithuanian"),
//        "ltz" to Length2AndName("lb", "Letzeburgesch, Luxembourgish"),
//        "lub" to Length2AndName("lu", "Luba-Katanga"),
//        "lug" to Length2AndName("lg", "Ganda"),
//        "mac" to Length2AndName("mk", "Macedonian"),
//        "mah" to Length2AndName("mh", "Marshallese"),
//        "mal" to Length2AndName("ml", "Malayalam"),
//        "mao" to Length2AndName("mi", "Maori"),
//        "mar" to Length2AndName("mr", "Marathi"),
//        "may" to Length2AndName("ms", "Malay (macrolanguage)"),
//        "mlg" to Length2AndName("mg", "Malagasy"),
//        "mlt" to Length2AndName("mt", "Maltese"),
//        "mon" to Length2AndName("mn", "Mongolian"),
//        "nau" to Length2AndName("na", "Nauru"),
//        "nav" to Length2AndName("nv", "Navaho, Navajo"),
//        "nbl" to Length2AndName("nr", "South Ndebele"),
//        "nde" to Length2AndName("nd", "North Ndebele"),
//        "ndo" to Length2AndName("ng", "Ndonga"),
//        "nep" to Length2AndName("ne", "Nepali (macrolanguage)"),
//        "nld" to Length2AndName("nl", "Dutch, Flemish"),
//        "nno" to Length2AndName("nn", "Norwegian Nynorsk"),
//        "nob" to Length2AndName("nb", "Norwegian Bokmål"),
//        "nor" to Length2AndName("no", "Norwegian"),
//        "nya" to Length2AndName("ny", "Chewa, Chichewa, Nyanja"),
//        "oci" to Length2AndName("oc", "Occitan (post 1500)"),
//        "oji" to Length2AndName("oj", "Ojibwa"),
//        "ori" to Length2AndName("or", "Oriya (macrolanguage)"),
//        "orm" to Length2AndName("om", "Oromo"),
//        "oss" to Length2AndName("os", "Ossetian, Ossetic"),
//        "pan" to Length2AndName("pa", "Panjabi, Punjabi"),
//        "per" to Length2AndName("fa", "Persian"),
//        "pes" to Length2AndName("pes", "Persian, Iranian"), // No 2-char code.
//        "pli" to Length2AndName("pi", "Pali"),
//        "pol" to Length2AndName("pl", "Polish"),
//        "por" to Length2AndName("pt", "Portuguese"),
//        "pus" to Length2AndName("ps", "Pashto, Pushto"),
//        "que" to Length2AndName("qu", "Quechua"),
//        "roh" to Length2AndName("rm", "Romansh"),
//        "ron" to Length2AndName("ro", "Romanian"),
//        "rum" to Length2AndName("ro", "Moldavian, Moldovan, Romanian"),
//        "run" to Length2AndName("rn", "Rundi"),
//        "rus" to Length2AndName("ru", "Russian"),
//        "sag" to Length2AndName("sg", "Sango"),
//        "san" to Length2AndName("sa", "Sanskrit"),
//        "sin" to Length2AndName("si", "Sinhala, Sinhalese"),
//        "slo" to Length2AndName("sk", "Slovak"),
//        "slv" to Length2AndName("sl", "Slovenian"),
//        "sme" to Length2AndName("se", "Northern Sami"),
//        "smo" to Length2AndName("sm", "Samoan"),
//        "sna" to Length2AndName("sn", "Shona"),
//        "snd" to Length2AndName("sd", "Sindhi"),
//        "som" to Length2AndName("so", "Somali"),
//        "sot" to Length2AndName("st", "Southern Sotho"),
//        "spa" to Length2AndName("es", "Castilian, Spanish"),
//        "srd" to Length2AndName("sc", "Sardinian"),
//        "srp" to Length2AndName("sr", "Serbian"),
//        "ssw" to Length2AndName("ss", "Swati"),
//        "sun" to Length2AndName("su", "Sundanese"),
//        "swa" to Length2AndName("sw", "Swahili (macrolanguage)"),
//        "swe" to Length2AndName("sv", "Swedish"),
//        "swh" to Length2AndName("swh", "Swahili"), // No 2-char code.
//        "tah" to Length2AndName("ty", "Tahitian"),
//        "tam" to Length2AndName("ta", "Tamil"),
//        "tat" to Length2AndName("tt", "Tatar"),
//        "tel" to Length2AndName("te", "Telugu"),
//        "tgk" to Length2AndName("tg", "Tajik"),
//        "tgl" to Length2AndName("tl", "Tagalog"),
//        "tha" to Length2AndName("th", "Thai"),
//        "tib" to Length2AndName("bo", "Tibetan"),
//        "tir" to Length2AndName("ti", "Tigrinya"),
//        "ton" to Length2AndName("to", "Tonga (Tonga Islands)"),
//        "tsn" to Length2AndName("tn", "Tswana"),
//        "tso" to Length2AndName("ts", "Tsonga"),
//        "tuk" to Length2AndName("tk", "Turkmen"),
//        "tur" to Length2AndName("tr", "Turkish"),
//        "twi" to Length2AndName("tw", "Twi"),
//        "uig" to Length2AndName("ug", "Uighur, Uyghur"),
//        "ukr" to Length2AndName("uk", "Ukrainian"),
//        "urd" to Length2AndName("ur", "Urdu"),
//        "uzb" to Length2AndName("uz", "Uzbek"),
//        "ven" to Length2AndName("ve", "Venda"),
//        "vie" to Length2AndName("vi", "Vietnamese"),
//        "vol" to Length2AndName("vo", "Volapük"),
//        "wel" to Length2AndName("cy", "Welsh"),
//        "wln" to Length2AndName("wa", "Walloon"),
//        "wol" to Length2AndName("wo", "Wolof"),
//        "xho" to Length2AndName("xh", "Xhosa"),
//        "yid" to Length2AndName("yi", "Yiddish"),
//        "yom" to Length2AndName("yom", "Yombe"), // No 2-char code.
//        "yor" to Length2AndName("yo", "Yoruba"),
//        "zha" to Length2AndName("za", "Chuang, Zhuang"),
//        "zom" to Length2AndName("zo", "Zokam"), // Not in ISO tables; took this from Ethnologue.
//        "zul" to Length2AndName("zu", "Zulu"),
//        )

  /****************************************************************************/
  private val m_2CharToPreferred3Char: MutableMap<String, String> = mutableMapOf()
  private val m_3CharToPreferred3Char: MutableMap<String, String> = mutableMapOf()
  private val m_Preferred3CharToDetails: MutableMap<String, Pair<String?, String>> = mutableMapOf()


  /****************************************************************************/
  private fun addIso639_2_LanguageDetails (preferred3Char: String, nonPreferred3Char: String?, x2Char: String?, name: String)
  {
    if (null != x2Char) m_2CharToPreferred3Char[x2Char] = preferred3Char
    if (null != nonPreferred3Char) m_3CharToPreferred3Char[nonPreferred3Char] = preferred3Char
    m_3CharToPreferred3Char[preferred3Char] = preferred3Char
    m_Preferred3CharToDetails[preferred3Char] = Pair(x2Char, name)
  }


  /****************************************************************************/
  init
  {
    // m_3To2.forEach { m_2To3[it.value.twoCharacterCode] = it.key }
    addIso639_2_LanguageDetails("aar", null, "aa", "Afar")
    addIso639_2_LanguageDetails("abk", null, "ab", "Abkhazian")
    addIso639_2_LanguageDetails("ace", null, null, "Achinese")
    addIso639_2_LanguageDetails("ach", null, null, "Acoli")
    addIso639_2_LanguageDetails("ada", null, null, "Adangme")
    addIso639_2_LanguageDetails("ady", null, null, "Adyghe; Adygei")
    addIso639_2_LanguageDetails("afa", null, null, "Afro-Asiatic languages")
    addIso639_2_LanguageDetails("afh", null, null, "Afrihili")
    addIso639_2_LanguageDetails("afr", null, "af", "Afrikaans")
    addIso639_2_LanguageDetails("ain", null, null, "Ainu")
    addIso639_2_LanguageDetails("aka", null, "ak", "Akan")
    addIso639_2_LanguageDetails("akk", null, null, "Akkadian")
    addIso639_2_LanguageDetails("alb", "sqi", "sq", "Albanian")
    addIso639_2_LanguageDetails("ale", null, null, "Aleut")
    addIso639_2_LanguageDetails("alg", null, null, "Algonquian languages")
    addIso639_2_LanguageDetails("alt", null, null, "Southern Altai")
    addIso639_2_LanguageDetails("amh", null, "am", "Amharic")
    addIso639_2_LanguageDetails("ang", null, null, "English, Old (ca.450-1100)")
    addIso639_2_LanguageDetails("anp", null, null, "Angika")
    addIso639_2_LanguageDetails("apa", null, null, "Apache languages")
    addIso639_2_LanguageDetails("ara", null, "ar", "Arabic")
    addIso639_2_LanguageDetails("arc", null, null, "Official Aramaic (700-300 BCE); Imperial Aramaic (700-300 BCE)")
    addIso639_2_LanguageDetails("arg", null, "an", "Aragonese")
    addIso639_2_LanguageDetails("arm", "hye", "hy", "Armenian")
    addIso639_2_LanguageDetails("arn", null, null, "Mapudungun; Mapuche")
    addIso639_2_LanguageDetails("arp", null, null, "Arapaho")
    addIso639_2_LanguageDetails("art", null, null, "Artificial languages")
    addIso639_2_LanguageDetails("arw", null, null, "Arawak")
    addIso639_2_LanguageDetails("asm", null, "as", "Assamese")
    addIso639_2_LanguageDetails("ast", null, null, "Asturian; Bable; Leonese; Asturleonese")
    addIso639_2_LanguageDetails("ath", null, null, "Athapascan languages")
    addIso639_2_LanguageDetails("aus", null, null, "Australian languages")
    addIso639_2_LanguageDetails("ava", null, "av", "Avaric")
    addIso639_2_LanguageDetails("ave", null, "ae", "Avestan")
    addIso639_2_LanguageDetails("awa", null, null, "Awadhi")
    addIso639_2_LanguageDetails("aym", null, "ay", "Aymara")
    addIso639_2_LanguageDetails("aze", null, "az", "Azerbaijani")
    addIso639_2_LanguageDetails("bad", null, null, "Banda languages")
    addIso639_2_LanguageDetails("bai", null, null, "Bamileke languages")
    addIso639_2_LanguageDetails("bak", null, "ba", "Bashkir")
    addIso639_2_LanguageDetails("bal", null, null, "Baluchi")
    addIso639_2_LanguageDetails("bam", null, "bm", "Bambara")
    addIso639_2_LanguageDetails("ban", null, null, "Balinese")
    addIso639_2_LanguageDetails("baq", "eus", "eu", "Basque")
    addIso639_2_LanguageDetails("bas", null, null, "Basa")
    addIso639_2_LanguageDetails("bat", null, null, "Baltic languages")
    addIso639_2_LanguageDetails("bej", null, null, "Beja; Bedawiyet")
    addIso639_2_LanguageDetails("bel", null, "be", "Belarusian")
    addIso639_2_LanguageDetails("bem", null, null, "Bemba")
    addIso639_2_LanguageDetails("ben", null, "bn", "Bengali")
    addIso639_2_LanguageDetails("ber", null, null, "Berber languages")
    addIso639_2_LanguageDetails("bho", null, null, "Bhojpuri")
    addIso639_2_LanguageDetails("bih", null, "bh", "Bihari languages")
    addIso639_2_LanguageDetails("bik", null, null, "Bikol")
    addIso639_2_LanguageDetails("bin", null, null, "Bini; Edo")
    addIso639_2_LanguageDetails("bis", null, "bi", "Bislama")
    addIso639_2_LanguageDetails("bla", null, null, "Siksika")
    addIso639_2_LanguageDetails("bnt", null, null, "Bantu languages")
    addIso639_2_LanguageDetails("bos", null, "bs", "Bosnian")
    addIso639_2_LanguageDetails("bra", null, null, "Braj")
    addIso639_2_LanguageDetails("bre", null, "br", "Breton")
    addIso639_2_LanguageDetails("btk", null, null, "Batak languages")
    addIso639_2_LanguageDetails("bua", null, null, "Buriat")
    addIso639_2_LanguageDetails("bug", null, null, "Buginese")
    addIso639_2_LanguageDetails("bul", null, "bg", "Bulgarian")
    addIso639_2_LanguageDetails("bur", "mya", "my", "Burmese")
    addIso639_2_LanguageDetails("byn", null, null, "Blin; Bilin")
    addIso639_2_LanguageDetails("cad", null, null, "Caddo")
    addIso639_2_LanguageDetails("cai", null, null, "Central American Indian languages")
    addIso639_2_LanguageDetails("car", null, null, "Galibi Carib")
    addIso639_2_LanguageDetails("cat", null, "ca", "Catalan; Valencian")
    addIso639_2_LanguageDetails("cau", null, null, "Caucasian languages")
    addIso639_2_LanguageDetails("ceb", null, null, "Cebuano")
    addIso639_2_LanguageDetails("cel", null, null, "Celtic languages")
    addIso639_2_LanguageDetails("cha", null, "ch", "Chamorro")
    addIso639_2_LanguageDetails("chb", null, null, "Chibcha")
    addIso639_2_LanguageDetails("che", null, "ce", "Chechen")
    addIso639_2_LanguageDetails("chg", null, null, "Chagatai")
    addIso639_2_LanguageDetails("chi", "zho", "zh", "Chinese")
    addIso639_2_LanguageDetails("chk", null, null, "Chuukese")
    addIso639_2_LanguageDetails("chm", null, null, "Mari")
    addIso639_2_LanguageDetails("chn", null, null, "Chinook jargon")
    addIso639_2_LanguageDetails("cho", null, null, "Choctaw")
    addIso639_2_LanguageDetails("chp", null, null, "Chipewyan; Dene Suline")
    addIso639_2_LanguageDetails("chr", null, null, "Cherokee")
    addIso639_2_LanguageDetails("chu", null, "cu", "Church Slavic; Old Slavonic; Church Slavonic; Old Bulgarian; Old Church Slavonic")
    addIso639_2_LanguageDetails("chv", null, "cv", "Chuvash")
    addIso639_2_LanguageDetails("chy", null, null, "Cheyenne")
    addIso639_2_LanguageDetails("cmc", null, null, "Chamic languages")
    addIso639_2_LanguageDetails("cnr", null, null, "Montenegrin")
    addIso639_2_LanguageDetails("cop", null, null, "Coptic")
    addIso639_2_LanguageDetails("cor", null, "kw", "Cornish")
    addIso639_2_LanguageDetails("cos", null, "co", "Corsican")
    addIso639_2_LanguageDetails("cpe", null, null, "Creoles and pidgins, English based")
    addIso639_2_LanguageDetails("cpf", null, null, "Creoles and pidgins, French-based")
    addIso639_2_LanguageDetails("cpp", null, null, "Creoles and pidgins, Portuguese-based")
    addIso639_2_LanguageDetails("cre", null, "cr", "Cree")
    addIso639_2_LanguageDetails("crh", null, null, "Crimean Tatar; Crimean Turkish")
    addIso639_2_LanguageDetails("crp", null, null, "Creoles and pidgins")
    addIso639_2_LanguageDetails("csb", null, null, "Kashubian")
    addIso639_2_LanguageDetails("cus", null, null, "Cushitic languages")
    addIso639_2_LanguageDetails("cze", "ces", "cs", "Czech")
    addIso639_2_LanguageDetails("dak", null, null, "Dakota")
    addIso639_2_LanguageDetails("dan", null, "da", "Danish")
    addIso639_2_LanguageDetails("dar", null, null, "Dargwa")
    addIso639_2_LanguageDetails("day", null, null, "Land Dayak languages")
    addIso639_2_LanguageDetails("del", null, null, "Delaware")
    addIso639_2_LanguageDetails("den", null, null, "Slave (Athapascan)")
    addIso639_2_LanguageDetails("dgr", null, null, "Dogrib")
    addIso639_2_LanguageDetails("din", null, null, "Dinka")
    addIso639_2_LanguageDetails("div", null, "dv", "Divehi; Dhivehi; Maldivian")
    addIso639_2_LanguageDetails("doi", null, null, "Dogri")
    addIso639_2_LanguageDetails("dra", null, null, "Dravidian languages")
    addIso639_2_LanguageDetails("dsb", null, null, "Lower Sorbian")
    addIso639_2_LanguageDetails("dua", null, null, "Duala")
    addIso639_2_LanguageDetails("dum", null, null, "Dutch, Middle (ca.1050-1350)")
    addIso639_2_LanguageDetails("dut", "nld", "nl", "Dutch; Flemish")
    addIso639_2_LanguageDetails("dyu", null, null, "Dyula")
    addIso639_2_LanguageDetails("dzo", null, "dz", "Dzongkha")
    addIso639_2_LanguageDetails("efi", null, null, "Efik")
    addIso639_2_LanguageDetails("egy", null, null, "Egyptian (Ancient)")
    addIso639_2_LanguageDetails("eka", null, null, "Ekajuk")
    addIso639_2_LanguageDetails("elx", null, null, "Elamite")
    addIso639_2_LanguageDetails("eng", null, "en", "English")
    addIso639_2_LanguageDetails("enm", null, null, "English, Middle (1100-1500)")
    addIso639_2_LanguageDetails("epo", null, "eo", "Esperanto")
    addIso639_2_LanguageDetails("est", null, "et", "Estonian")
    addIso639_2_LanguageDetails("ewe", null, "ee", "Ewe")
    addIso639_2_LanguageDetails("ewo", null, null, "Ewondo")
    addIso639_2_LanguageDetails("fan", null, null, "Fang")
    addIso639_2_LanguageDetails("fao", null, "fo", "Faroese")
    addIso639_2_LanguageDetails("fat", null, null, "Fanti")
    addIso639_2_LanguageDetails("fij", null, "fj", "Fijian")
    addIso639_2_LanguageDetails("fil", null, null, "Filipino; Pilipino")
    addIso639_2_LanguageDetails("fin", null, "fi", "Finnish")
    addIso639_2_LanguageDetails("fiu", null, null, "Finno-Ugrian languages")
    addIso639_2_LanguageDetails("fon", null, null, "Fon")
    addIso639_2_LanguageDetails("fre", "fra", "fr", "French")
    addIso639_2_LanguageDetails("frm", null, null, "French, Middle (ca.1400-1600)")
    addIso639_2_LanguageDetails("fro", null, null, "French, Old (842-ca.1400)")
    addIso639_2_LanguageDetails("frr", null, null, "Northern Frisian")
    addIso639_2_LanguageDetails("frs", null, null, "Eastern Frisian")
    addIso639_2_LanguageDetails("fry", null, "fy", "Western Frisian")
    addIso639_2_LanguageDetails("ful", null, "ff", "Fulah")
    addIso639_2_LanguageDetails("fur", null, null, "Friulian")
    addIso639_2_LanguageDetails("gaa", null, null, "Ga")
    addIso639_2_LanguageDetails("gay", null, null, "Gayo")
    addIso639_2_LanguageDetails("gba", null, null, "Gbaya")
    addIso639_2_LanguageDetails("gem", null, null, "Germanic languages")
    addIso639_2_LanguageDetails("geo", "kat", "ka", "Georgian")
    addIso639_2_LanguageDetails("ger", "deu", "de", "German")
    addIso639_2_LanguageDetails("gez", null, null, "Geez")
    addIso639_2_LanguageDetails("gil", null, null, "Gilbertese")
    addIso639_2_LanguageDetails("gla", null, "gd", "Gaelic; Scottish Gaelic")
    addIso639_2_LanguageDetails("gle", null, "ga", "Irish")
    addIso639_2_LanguageDetails("glg", null, "gl", "Galician")
    addIso639_2_LanguageDetails("glv", null, "gv", "Manx")
    addIso639_2_LanguageDetails("gmh", null, null, "German, Middle High (ca.1050-1500)")
    addIso639_2_LanguageDetails("goh", null, null, "German, Old High (ca.750-1050)")
    addIso639_2_LanguageDetails("gon", null, null, "Gondi")
    addIso639_2_LanguageDetails("gor", null, null, "Gorontalo")
    addIso639_2_LanguageDetails("got", null, null, "Gothic")
    addIso639_2_LanguageDetails("grb", null, null, "Grebo")
    addIso639_2_LanguageDetails("grc", null, null, "Greek, Ancient (to 1453)")
    addIso639_2_LanguageDetails("gre", "ell", "el", "Greek, Modern (1453-)")
    addIso639_2_LanguageDetails("grn", null, "gn", "Guarani")
    addIso639_2_LanguageDetails("gsw", null, null, "Swiss German; Alemannic; Alsatian")
    addIso639_2_LanguageDetails("guj", null, "gu", "Gujarati")
    addIso639_2_LanguageDetails("gwi", null, null, "Gwich'in")
    addIso639_2_LanguageDetails("hai", null, null, "Haida")
    addIso639_2_LanguageDetails("hat", null, "ht", "Haitian; Haitian Creole")
    addIso639_2_LanguageDetails("hau", null, "ha", "Hausa")
    addIso639_2_LanguageDetails("haw", null, null, "Hawaiian")
    addIso639_2_LanguageDetails("heb", null, "he", "Hebrew")
    addIso639_2_LanguageDetails("her", null, "hz", "Herero")
    addIso639_2_LanguageDetails("hil", null, null, "Hiligaynon")
    addIso639_2_LanguageDetails("him", null, null, "Himachali languages; Western Pahari languages")
    addIso639_2_LanguageDetails("hin", null, "hi", "Hindi")
    addIso639_2_LanguageDetails("hit", null, null, "Hittite")
    addIso639_2_LanguageDetails("hmn", null, null, "Hmong; Mong")
    addIso639_2_LanguageDetails("hmo", null, "ho", "Hiri Motu")
    addIso639_2_LanguageDetails("hrv", null, "hr", "Croatian")
    addIso639_2_LanguageDetails("hsb", null, null, "Upper Sorbian")
    addIso639_2_LanguageDetails("hun", null, "hu", "Hungarian")
    addIso639_2_LanguageDetails("hup", null, null, "Hupa")
    addIso639_2_LanguageDetails("iba", null, null, "Iban")
    addIso639_2_LanguageDetails("ibo", null, "ig", "Igbo")
    addIso639_2_LanguageDetails("ice", "isl", "is", "Icelandic")
    addIso639_2_LanguageDetails("ido", null, "io", "Ido")
    addIso639_2_LanguageDetails("iii", null, "ii", "Sichuan Yi; Nuosu")
    addIso639_2_LanguageDetails("ijo", null, null, "Ijo languages")
    addIso639_2_LanguageDetails("iku", null, "iu", "Inuktitut")
    addIso639_2_LanguageDetails("ile", null, "ie", "Interlingue; Occidental")
    addIso639_2_LanguageDetails("ilo", null, null, "Iloko")
    addIso639_2_LanguageDetails("ina", null, "ia", "Interlingua (International Auxiliary Language Association)")
    addIso639_2_LanguageDetails("inc", null, null, "Indic languages")
    addIso639_2_LanguageDetails("ind", null, "id", "Indonesian")
    addIso639_2_LanguageDetails("ine", null, null, "Indo-European languages")
    addIso639_2_LanguageDetails("inh", null, null, "Ingush")
    addIso639_2_LanguageDetails("ipk", null, "ik", "Inupiaq")
    addIso639_2_LanguageDetails("ira", null, null, "Iranian languages")
    addIso639_2_LanguageDetails("iro", null, null, "Iroquoian languages")
    addIso639_2_LanguageDetails("ita", null, "it", "Italian")
    addIso639_2_LanguageDetails("jav", null, "jv", "Javanese")
    addIso639_2_LanguageDetails("jbo", null, null, "Lojban")
    addIso639_2_LanguageDetails("jpn", null, "ja", "Japanese")
    addIso639_2_LanguageDetails("jpr", null, null, "Judeo-Persian")
    addIso639_2_LanguageDetails("jrb", null, null, "Judeo-Arabic")
    addIso639_2_LanguageDetails("kaa", null, null, "Kara-Kalpak")
    addIso639_2_LanguageDetails("kab", null, null, "Kabyle")
    addIso639_2_LanguageDetails("kac", null, null, "Kachin; Jingpho")
    addIso639_2_LanguageDetails("kal", null, "kl", "Kalaallisut; Greenlandic")
    addIso639_2_LanguageDetails("kam", null, null, "Kamba")
    addIso639_2_LanguageDetails("kan", null, "kn", "Kannada")
    addIso639_2_LanguageDetails("kar", null, null, "Karen languages")
    addIso639_2_LanguageDetails("kas", null, "ks", "Kashmiri")
    addIso639_2_LanguageDetails("kau", null, "kr", "Kanuri")
    addIso639_2_LanguageDetails("kaw", null, null, "Kawi")
    addIso639_2_LanguageDetails("kaz", null, "kk", "Kazakh")
    addIso639_2_LanguageDetails("kbd", null, null, "Kabardian")
    addIso639_2_LanguageDetails("kha", null, null, "Khasi")
    addIso639_2_LanguageDetails("khi", null, null, "Khoisan languages")
    addIso639_2_LanguageDetails("khm", null, "km", "Central Khmer")
    addIso639_2_LanguageDetails("kho", null, null, "Khotanese; Sakan")
    addIso639_2_LanguageDetails("kik", null, "ki", "Kikuyu; Gikuyu")
    addIso639_2_LanguageDetails("kin", null, "rw", "Kinyarwanda")
    addIso639_2_LanguageDetails("kir", null, "ky", "Kirghiz; Kyrgyz")
    addIso639_2_LanguageDetails("kmb", null, null, "Kimbundu")
    addIso639_2_LanguageDetails("kok", null, null, "Konkani")
    addIso639_2_LanguageDetails("kom", null, "kv", "Komi")
    addIso639_2_LanguageDetails("kon", null, "kg", "Kongo")
    addIso639_2_LanguageDetails("kor", null, "ko", "Korean")
    addIso639_2_LanguageDetails("kos", null, null, "Kosraean")
    addIso639_2_LanguageDetails("kpe", null, null, "Kpelle")
    addIso639_2_LanguageDetails("krc", null, null, "Karachay-Balkar")
    addIso639_2_LanguageDetails("krl", null, null, "Karelian")
    addIso639_2_LanguageDetails("kro", null, null, "Kru languages")
    addIso639_2_LanguageDetails("kru", null, null, "Kurukh")
    addIso639_2_LanguageDetails("kua", null, "kj", "Kuanyama; Kwanyama")
    addIso639_2_LanguageDetails("kum", null, null, "Kumyk")
    addIso639_2_LanguageDetails("kur", null, "ku", "Kurdish")
    addIso639_2_LanguageDetails("kut", null, null, "Kutenai")
    addIso639_2_LanguageDetails("lad", null, null, "Ladino")
    addIso639_2_LanguageDetails("lah", null, null, "Lahnda")
    addIso639_2_LanguageDetails("lam", null, null, "Lamba")
    addIso639_2_LanguageDetails("lao", null, "lo", "Lao")
    addIso639_2_LanguageDetails("lat", null, "la", "Latin")
    addIso639_2_LanguageDetails("lav", null, "lv", "Latvian")
    addIso639_2_LanguageDetails("lez", null, null, "Lezghian")
    addIso639_2_LanguageDetails("lim", null, "li", "Limburgan; Limburger; Limburgish")
    addIso639_2_LanguageDetails("lin", null, "ln", "Lingala")
    addIso639_2_LanguageDetails("lit", null, "lt", "Lithuanian")
    addIso639_2_LanguageDetails("lol", null, null, "Mongo")
    addIso639_2_LanguageDetails("loz", null, null, "Lozi")
    addIso639_2_LanguageDetails("ltz", null, "lb", "Luxembourgish; Letzeburgesch")
    addIso639_2_LanguageDetails("lua", null, null, "Luba-Lulua")
    addIso639_2_LanguageDetails("lub", null, "lu", "Luba-Katanga")
    addIso639_2_LanguageDetails("lug", null, "lg", "Ganda")
    addIso639_2_LanguageDetails("lui", null, null, "Luiseno")
    addIso639_2_LanguageDetails("lun", null, null, "Lunda")
    addIso639_2_LanguageDetails("luo", null, null, "Luo (Kenya and Tanzania)")
    addIso639_2_LanguageDetails("lus", null, null, "Lushai")
    addIso639_2_LanguageDetails("mac", "mkd", "mk", "Macedonian")
    addIso639_2_LanguageDetails("mad", null, null, "Madurese")
    addIso639_2_LanguageDetails("mag", null, null, "Magahi")
    addIso639_2_LanguageDetails("mah", null, "mh", "Marshallese")
    addIso639_2_LanguageDetails("mai", null, null, "Maithili")
    addIso639_2_LanguageDetails("mak", null, null, "Makasar")
    addIso639_2_LanguageDetails("mal", null, "ml", "Malayalam")
    addIso639_2_LanguageDetails("man", null, null, "Mandingo")
    addIso639_2_LanguageDetails("mao", "mri", "mi", "Maori")
    addIso639_2_LanguageDetails("map", null, null, "Austronesian languages")
    addIso639_2_LanguageDetails("mar", null, "mr", "Marathi")
    addIso639_2_LanguageDetails("mas", null, null, "Masai")
    addIso639_2_LanguageDetails("may", "msa", "ms", "Malay")
    addIso639_2_LanguageDetails("mdf", null, null, "Moksha")
    addIso639_2_LanguageDetails("mdr", null, null, "Mandar")
    addIso639_2_LanguageDetails("men", null, null, "Mende")
    addIso639_2_LanguageDetails("mga", null, null, "Irish, Middle (900-1200)")
    addIso639_2_LanguageDetails("mic", null, null, "Mi'kmaq; Micmac")
    addIso639_2_LanguageDetails("min", null, null, "Minangkabau")
    addIso639_2_LanguageDetails("mis", null, null, "Uncoded languages")
    addIso639_2_LanguageDetails("mkh", null, null, "Mon-Khmer languages")
    addIso639_2_LanguageDetails("mlg", null, "mg", "Malagasy")
    addIso639_2_LanguageDetails("mlt", null, "mt", "Maltese")
    addIso639_2_LanguageDetails("mnc", null, null, "Manchu")
    addIso639_2_LanguageDetails("mni", null, null, "Manipuri")
    addIso639_2_LanguageDetails("mno", null, null, "Manobo languages")
    addIso639_2_LanguageDetails("moh", null, null, "Mohawk")
    addIso639_2_LanguageDetails("mon", null, "mn", "Mongolian")
    addIso639_2_LanguageDetails("mos", null, null, "Mossi")
    addIso639_2_LanguageDetails("mul", null, null, "Multiple languages")
    addIso639_2_LanguageDetails("mun", null, null, "Munda languages")
    addIso639_2_LanguageDetails("mus", null, null, "Creek")
    addIso639_2_LanguageDetails("mwl", null, null, "Mirandese")
    addIso639_2_LanguageDetails("mwr", null, null, "Marwari")
    addIso639_2_LanguageDetails("myn", null, null, "Mayan languages")
    addIso639_2_LanguageDetails("myv", null, null, "Erzya")
    addIso639_2_LanguageDetails("nah", null, null, "Nahuatl languages")
    addIso639_2_LanguageDetails("nai", null, null, "North American Indian languages")
    addIso639_2_LanguageDetails("nap", null, null, "Neapolitan")
    addIso639_2_LanguageDetails("nau", null, "na", "Nauru")
    addIso639_2_LanguageDetails("nav", null, "nv", "Navajo; Navaho")
    addIso639_2_LanguageDetails("nbl", null, "nr", "Ndebele, South; South Ndebele")
    addIso639_2_LanguageDetails("nde", null, "nd", "Ndebele, North; North Ndebele")
    addIso639_2_LanguageDetails("ndo", null, "ng", "Ndonga")
    addIso639_2_LanguageDetails("nds", null, null, "Low German; Low Saxon; German, Low; Saxon, Low")
    addIso639_2_LanguageDetails("nep", null, "ne", "Nepali")
    addIso639_2_LanguageDetails("new", null, null, "Nepal Bhasa; Newari")
    addIso639_2_LanguageDetails("nia", null, null, "Nias")
    addIso639_2_LanguageDetails("nic", null, null, "Niger-Kordofanian languages")
    addIso639_2_LanguageDetails("niu", null, null, "Niuean")
    addIso639_2_LanguageDetails("nno", null, "nn", "Norwegian Nynorsk; Nynorsk, Norwegian")
    addIso639_2_LanguageDetails("nob", null, "nb", "Bokmål, Norwegian; Norwegian Bokmål")
    addIso639_2_LanguageDetails("nog", null, null, "Nogai")
    addIso639_2_LanguageDetails("non", null, null, "Norse, Old")
    addIso639_2_LanguageDetails("nor", null, "no", "Norwegian")
    addIso639_2_LanguageDetails("nqo", null, null, "N'Ko")
    addIso639_2_LanguageDetails("nso", null, null, "Pedi; Sepedi; Northern Sotho")
    addIso639_2_LanguageDetails("nub", null, null, "Nubian languages")
    addIso639_2_LanguageDetails("nwc", null, null, "Classical Newari; Old Newari; Classical Nepal Bhasa")
    addIso639_2_LanguageDetails("nya", null, "ny", "Chichewa; Chewa; Nyanja")
    addIso639_2_LanguageDetails("nym", null, null, "Nyamwezi")
    addIso639_2_LanguageDetails("nyn", null, null, "Nyankole")
    addIso639_2_LanguageDetails("nyo", null, null, "Nyoro")
    addIso639_2_LanguageDetails("nzi", null, null, "Nzima")
    addIso639_2_LanguageDetails("oci", null, "oc", "Occitan (post 1500)")
    addIso639_2_LanguageDetails("oji", null, "oj", "Ojibwa")
    addIso639_2_LanguageDetails("ori", null, "or", "Oriya")
    addIso639_2_LanguageDetails("orm", null, "om", "Oromo")
    addIso639_2_LanguageDetails("osa", null, null, "Osage")
    addIso639_2_LanguageDetails("oss", null, "os", "Ossetian; Ossetic")
    addIso639_2_LanguageDetails("ota", null, null, "Turkish, Ottoman (1500-1928)")
    addIso639_2_LanguageDetails("oto", null, null, "Otomian languages")
    addIso639_2_LanguageDetails("paa", null, null, "Papuan languages")
    addIso639_2_LanguageDetails("pag", null, null, "Pangasinan")
    addIso639_2_LanguageDetails("pal", null, null, "Pahlavi")
    addIso639_2_LanguageDetails("pam", null, null, "Pampanga; Kapampangan")
    addIso639_2_LanguageDetails("pan", null, "pa", "Panjabi; Punjabi")
    addIso639_2_LanguageDetails("pap", null, null, "Papiamento")
    addIso639_2_LanguageDetails("pau", null, null, "Palauan")
    addIso639_2_LanguageDetails("peo", null, null, "Persian, Old (ca.600-400 B.C.)")
    addIso639_2_LanguageDetails("per", "fas", "fa", "Persian")
    addIso639_2_LanguageDetails("phi", null, null, "Philippine languages")
    addIso639_2_LanguageDetails("phn", null, null, "Phoenician")
    addIso639_2_LanguageDetails("pli", null, "pi", "Pali")
    addIso639_2_LanguageDetails("pol", null, "pl", "Polish")
    addIso639_2_LanguageDetails("pon", null, null, "Pohnpeian")
    addIso639_2_LanguageDetails("por", null, "pt", "Portuguese")
    addIso639_2_LanguageDetails("pra", null, null, "Prakrit languages")
    addIso639_2_LanguageDetails("pro", null, null, "Provençal, Old (to 1500); Occitan, Old (to 1500)")
    addIso639_2_LanguageDetails("pus", null, "ps", "Pushto; Pashto")
    addIso639_2_LanguageDetails("qaa-qtz", null, null, "Reserved for local use")
    addIso639_2_LanguageDetails("que", null, "qu", "Quechua")
    addIso639_2_LanguageDetails("raj", null, null, "Rajasthani")
    addIso639_2_LanguageDetails("rap", null, null, "Rapanui")
    addIso639_2_LanguageDetails("rar", null, null, "Rarotongan; Cook Islands Maori")
    addIso639_2_LanguageDetails("roa", null, null, "Romance languages")
    addIso639_2_LanguageDetails("roh", null, "rm", "Romansh")
    addIso639_2_LanguageDetails("rom", null, null, "Romany")
    addIso639_2_LanguageDetails("rum", "ron", "ro", "Romanian; Moldavian; Moldovan")
    addIso639_2_LanguageDetails("run", null, "rn", "Rundi")
    addIso639_2_LanguageDetails("rup", null, null, "Aromanian; Arumanian; Macedo-Romanian")
    addIso639_2_LanguageDetails("rus", null, "ru", "Russian")
    addIso639_2_LanguageDetails("sad", null, null, "Sandawe")
    addIso639_2_LanguageDetails("sag", null, "sg", "Sango")
    addIso639_2_LanguageDetails("sah", null, null, "Yakut")
    addIso639_2_LanguageDetails("sai", null, null, "South American Indian languages")
    addIso639_2_LanguageDetails("sal", null, null, "Salishan languages")
    addIso639_2_LanguageDetails("sam", null, null, "Samaritan Aramaic")
    addIso639_2_LanguageDetails("san", null, "sa", "Sanskrit")
    addIso639_2_LanguageDetails("sas", null, null, "Sasak")
    addIso639_2_LanguageDetails("sat", null, null, "Santali")
    addIso639_2_LanguageDetails("scn", null, null, "Sicilian")
    addIso639_2_LanguageDetails("sco", null, null, "Scots")
    addIso639_2_LanguageDetails("sel", null, null, "Selkup")
    addIso639_2_LanguageDetails("sem", null, null, "Semitic languages")
    addIso639_2_LanguageDetails("sga", null, null, "Irish, Old (to 900)")
    addIso639_2_LanguageDetails("sgn", null, null, "Sign Languages")
    addIso639_2_LanguageDetails("shn", null, null, "Shan")
    addIso639_2_LanguageDetails("sid", null, null, "Sidamo")
    addIso639_2_LanguageDetails("sin", null, "si", "Sinhala; Sinhalese")
    addIso639_2_LanguageDetails("sio", null, null, "Siouan languages")
    addIso639_2_LanguageDetails("sit", null, null, "Sino-Tibetan languages")
    addIso639_2_LanguageDetails("sla", null, null, "Slavic languages")
    addIso639_2_LanguageDetails("slo", "slk", "sk", "Slovak")
    addIso639_2_LanguageDetails("slv", null, "sl", "Slovenian")
    addIso639_2_LanguageDetails("sma", null, null, "Southern Sami")
    addIso639_2_LanguageDetails("sme", null, "se", "Northern Sami")
    addIso639_2_LanguageDetails("smi", null, null, "Sami languages")
    addIso639_2_LanguageDetails("smj", null, null, "Lule Sami")
    addIso639_2_LanguageDetails("smn", null, null, "Inari Sami")
    addIso639_2_LanguageDetails("smo", null, "sm", "Samoan")
    addIso639_2_LanguageDetails("sms", null, null, "Skolt Sami")
    addIso639_2_LanguageDetails("sna", null, "sn", "Shona")
    addIso639_2_LanguageDetails("snd", null, "sd", "Sindhi")
    addIso639_2_LanguageDetails("snk", null, null, "Soninke")
    addIso639_2_LanguageDetails("sog", null, null, "Sogdian")
    addIso639_2_LanguageDetails("som", null, "so", "Somali")
    addIso639_2_LanguageDetails("son", null, null, "Songhai languages")
    addIso639_2_LanguageDetails("sot", null, "st", "Sotho, Southern")
    addIso639_2_LanguageDetails("spa", null, "es", "Spanish; Castilian")
    addIso639_2_LanguageDetails("srd", null, "sc", "Sardinian")
    addIso639_2_LanguageDetails("srn", null, null, "Sranan Tongo")
    addIso639_2_LanguageDetails("srp", null, "sr", "Serbian")
    addIso639_2_LanguageDetails("srr", null, null, "Serer")
    addIso639_2_LanguageDetails("ssa", null, null, "Nilo-Saharan languages")
    addIso639_2_LanguageDetails("ssw", null, "ss", "Swati")
    addIso639_2_LanguageDetails("suk", null, null, "Sukuma")
    addIso639_2_LanguageDetails("sun", null, "su", "Sundanese")
    addIso639_2_LanguageDetails("sus", null, null, "Susu")
    addIso639_2_LanguageDetails("sux", null, null, "Sumerian")
    addIso639_2_LanguageDetails("swa", null, "sw", "Swahili")
    addIso639_2_LanguageDetails("swe", null, "sv", "Swedish")
    addIso639_2_LanguageDetails("syc", null, null, "Classical Syriac")
    addIso639_2_LanguageDetails("syr", null, null, "Syriac")
    addIso639_2_LanguageDetails("tah", null, "ty", "Tahitian")
    addIso639_2_LanguageDetails("tai", null, null, "Tai languages")
    addIso639_2_LanguageDetails("tam", null, "ta", "Tamil")
    addIso639_2_LanguageDetails("tat", null, "tt", "Tatar")
    addIso639_2_LanguageDetails("tel", null, "te", "Telugu")
    addIso639_2_LanguageDetails("tem", null, null, "Timne")
    addIso639_2_LanguageDetails("ter", null, null, "Tereno")
    addIso639_2_LanguageDetails("tet", null, null, "Tetum")
    addIso639_2_LanguageDetails("tgk", null, "tg", "Tajik")
    addIso639_2_LanguageDetails("tgl", null, "tl", "Tagalog")
    addIso639_2_LanguageDetails("tha", null, "th", "Thai")
    addIso639_2_LanguageDetails("tib", "bod", "bo", "Tibetan")
    addIso639_2_LanguageDetails("tig", null, null, "Tigre")
    addIso639_2_LanguageDetails("tir", null, "ti", "Tigrinya")
    addIso639_2_LanguageDetails("tiv", null, null, "Tiv")
    addIso639_2_LanguageDetails("tkl", null, null, "Tokelau")
    addIso639_2_LanguageDetails("tlh", null, null, "Klingon; tlhIngan-Hol")
    addIso639_2_LanguageDetails("tli", null, null, "Tlingit")
    addIso639_2_LanguageDetails("tmh", null, null, "Tamashek")
    addIso639_2_LanguageDetails("tog", null, null, "Tonga (Nyasa)")
    addIso639_2_LanguageDetails("ton", null, "to", "Tonga (Tonga Islands)")
    addIso639_2_LanguageDetails("tpi", null, null, "Tok Pisin")
    addIso639_2_LanguageDetails("tsi", null, null, "Tsimshian")
    addIso639_2_LanguageDetails("tsn", null, "tn", "Tswana")
    addIso639_2_LanguageDetails("tso", null, "ts", "Tsonga")
    addIso639_2_LanguageDetails("tuk", null, "tk", "Turkmen")
    addIso639_2_LanguageDetails("tum", null, null, "Tumbuka")
    addIso639_2_LanguageDetails("tup", null, null, "Tupi languages")
    addIso639_2_LanguageDetails("tur", null, "tr", "Turkish")
    addIso639_2_LanguageDetails("tut", null, null, "Altaic languages")
    addIso639_2_LanguageDetails("tvl", null, null, "Tuvalu")
    addIso639_2_LanguageDetails("twi", null, "tw", "Twi")
    addIso639_2_LanguageDetails("tyv", null, null, "Tuvinian")
    addIso639_2_LanguageDetails("udm", null, null, "Udmurt")
    addIso639_2_LanguageDetails("uga", null, null, "Ugaritic")
    addIso639_2_LanguageDetails("uig", null, "ug", "Uighur; Uyghur")
    addIso639_2_LanguageDetails("ukr", null, "uk", "Ukrainian")
    addIso639_2_LanguageDetails("umb", null, null, "Umbundu")
    addIso639_2_LanguageDetails("und", null, null, "Undetermined")
    addIso639_2_LanguageDetails("urd", null, "ur", "Urdu")
    addIso639_2_LanguageDetails("uzb", null, "uz", "Uzbek")
    addIso639_2_LanguageDetails("vai", null, null, "Vai")
    addIso639_2_LanguageDetails("ven", null, "ve", "Venda")
    addIso639_2_LanguageDetails("vie", null, "vi", "Vietnamese")
    addIso639_2_LanguageDetails("vol", null, "vo", "Volapük")
    addIso639_2_LanguageDetails("vot", null, null, "Votic")
    addIso639_2_LanguageDetails("wak", null, null, "Wakashan languages")
    addIso639_2_LanguageDetails("wal", null, null, "Wolaitta; Wolaytta")
    addIso639_2_LanguageDetails("war", null, null, "Waray")
    addIso639_2_LanguageDetails("was", null, null, "Washo")
    addIso639_2_LanguageDetails("wel", "cym", "cy", "Welsh")
    addIso639_2_LanguageDetails("wen", null, null, "Sorbian languages")
    addIso639_2_LanguageDetails("wln", null, "wa", "Walloon")
    addIso639_2_LanguageDetails("wol", null, "wo", "Wolof")
    addIso639_2_LanguageDetails("xal", null, null, "Kalmyk; Oirat")
    addIso639_2_LanguageDetails("xho", null, "xh", "Xhosa")
    addIso639_2_LanguageDetails("yao", null, null, "Yao")
    addIso639_2_LanguageDetails("yap", null, null, "Yapese")
    addIso639_2_LanguageDetails("yid", null, "yi", "Yiddish")
    addIso639_2_LanguageDetails("yor", null, "yo", "Yoruba")
    addIso639_2_LanguageDetails("ypk", null, null, "Yupik languages")
    addIso639_2_LanguageDetails("zap", null, null, "Zapotec")
    addIso639_2_LanguageDetails("zbl", null, null, "Blissymbols; Blissymbolics; Bliss")
    addIso639_2_LanguageDetails("zen", null, null, "Zenaga")
    addIso639_2_LanguageDetails("zgh", null, null, "Standard Moroccan Tamazight")
    addIso639_2_LanguageDetails("zha", null, "za", "Zhuang; Chuang")
    addIso639_2_LanguageDetails("znd", null, null, "Zande languages")
    addIso639_2_LanguageDetails("zul", null, "zu", "Zulu")
    addIso639_2_LanguageDetails("zun", null, null, "Zuni")
    addIso639_2_LanguageDetails("zxx", null, null, "No linguistic content; Not applicable")
    addIso639_2_LanguageDetails("zza", null, null, "Zaza; Dimili; Dimli; Kirdki; Kirmanjki; Zazaki")

  }
}