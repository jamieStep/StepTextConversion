package org.stepbible.textconverter.support.iso


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
      val asSupplied = isoCode.lowercase()
      if (2 == isoCode.length) return isoCode.lowercase()
      return m_3CharToDetails[asSupplied]!!.first ?: asSupplied
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
      return m_3CharToDetails[key]!!.second
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
  private val m_2CharTo3Char: MutableMap<String, String> = mutableMapOf()
  private val m_3CharToDetails: MutableMap<String, Pair<String?, String>> = mutableMapOf()


  /****************************************************************************/
  private fun addIso639ToLanguageDetails (code3Char: String, code2Char: String?, name: String)
  {
    if (null != code2Char) m_2CharTo3Char[code2Char] = code3Char
    m_3CharToDetails[code3Char] = Pair(code2Char, name)
  }


  /****************************************************************************/
  init
  {
    addIso639ToLanguageDetails("aar", "aa", "Afar")
    addIso639ToLanguageDetails("abk", "ab", "Abkhazian")
    addIso639ToLanguageDetails("abm", null, "Abanyom") // Ethnologue.
    addIso639ToLanguageDetails("ace", null, "Achinese")
    addIso639ToLanguageDetails("ach", null, "Acoli")
    addIso639ToLanguageDetails("ada", null, "Adangme")
    addIso639ToLanguageDetails("ady", null, "Adyghe; Adygei")
    addIso639ToLanguageDetails("afa", null, "Afro-Asiatic languages")
    addIso639ToLanguageDetails("afh", null, "Afrihili")
    addIso639ToLanguageDetails("afr", "af", "Afrikaans")
    addIso639ToLanguageDetails("ain", null, "Ainu")
    addIso639ToLanguageDetails("aka", "ak", "Akan")
    addIso639ToLanguageDetails("akk", null, "Akkadian")
    addIso639ToLanguageDetails("alb", "sq", "Albanian")
    addIso639ToLanguageDetails("ale", null, "Aleut")
    addIso639ToLanguageDetails("alf", null, "Elege") // Ethnologue.
    addIso639ToLanguageDetails("alg", null, "Algonquian languages")
    addIso639ToLanguageDetails("alt", null, "Southern Altai")
    addIso639ToLanguageDetails("amh", "am", "Amharic")
    addIso639ToLanguageDetails("ang", null, "English, Old (ca.450-1100)")
    addIso639ToLanguageDetails("anp", null, "Angika")
    addIso639ToLanguageDetails("apa", null, "Apache languages")
    addIso639ToLanguageDetails("ara", "ar", "Arabic")
    addIso639ToLanguageDetails("arb", null, "Arabic")
    addIso639ToLanguageDetails("arc", null, "Official Aramaic (700-300 BCE); Imperial Aramaic (700-300 BCE)")
    addIso639ToLanguageDetails("arg", "an", "Aragonese")
    addIso639ToLanguageDetails("arm", "hy", "Armenian")
    addIso639ToLanguageDetails("arn", null, "Mapudungun; Mapuche")
    addIso639ToLanguageDetails("arp", null, "Arapaho")
    addIso639ToLanguageDetails("art", null, "Artificial languages")
    addIso639ToLanguageDetails("arw", null, "Arawak")
    addIso639ToLanguageDetails("asm", "as", "Assamese")
    addIso639ToLanguageDetails("ast", null, "Asturian; Bable; Leonese; Asturleonese")
    addIso639ToLanguageDetails("ath", null, "Athapascan languages")
    addIso639ToLanguageDetails("aus", null, "Australian languages")
    addIso639ToLanguageDetails("ava", "av", "Avaric")
    addIso639ToLanguageDetails("ave", "ae", "Avestan")
    addIso639ToLanguageDetails("awa", null, "Awadhi")
    addIso639ToLanguageDetails("aym", "ay", "Aymara")
    addIso639ToLanguageDetails("aze", "az", "Azerbaijani")
    addIso639ToLanguageDetails("bad", null, "Banda languages")
    addIso639ToLanguageDetails("bai", null, "Bamileke languages")
    addIso639ToLanguageDetails("bak", "ba", "Bashkir")
    addIso639ToLanguageDetails("bal", null, "Baluchi")
    addIso639ToLanguageDetails("bam", "bm", "Bambara")
    addIso639ToLanguageDetails("ban", null, "Balinese")
    addIso639ToLanguageDetails("baq", "eu", "Basque")
    addIso639ToLanguageDetails("bas", null, "Basa")
    addIso639ToLanguageDetails("bat", null, "Baltic languages")
    addIso639ToLanguageDetails("bej", null, "Beja; Bedawiyet")
    addIso639ToLanguageDetails("bel", "be", "Belarusian")
    addIso639ToLanguageDetails("bem", null, "Bemba")
    addIso639ToLanguageDetails("ben", "bn", "Bengali")
    addIso639ToLanguageDetails("ber", null, "Berber languages")
    addIso639ToLanguageDetails("bho", null, "Bhojpuri")
    addIso639ToLanguageDetails("bih", "bh", "Bihari languages")
    addIso639ToLanguageDetails("bik", null, "Bikol")
    addIso639ToLanguageDetails("bin", null, "Bini; Edo")
    addIso639ToLanguageDetails("bis", "bi", "Bislama")
    addIso639ToLanguageDetails("bla", null, "Siksika")
    addIso639ToLanguageDetails("bnt", null, "Bantu languages")
    addIso639ToLanguageDetails("bos", "bs", "Bosnian")
    addIso639ToLanguageDetails("bra", null, "Braj")
    addIso639ToLanguageDetails("bre", "br", "Breton")
    addIso639ToLanguageDetails("btk", null, "Batak languages")
    addIso639ToLanguageDetails("bua", null, "Buriat")
    addIso639ToLanguageDetails("bug", null, "Buginese")
    addIso639ToLanguageDetails("bul", "bg", "Bulgarian")
    addIso639ToLanguageDetails("bur", "my", "Burmese")
    addIso639ToLanguageDetails("bux", null, "Boghom") // Ethnologue.
    addIso639ToLanguageDetails("byn", null, "Blin; Bilin")
    addIso639ToLanguageDetails("cad", null, "Caddo")
    addIso639ToLanguageDetails("cai", null, "Central American Indian languages")
    addIso639ToLanguageDetails("car", null, "Galibi Carib")
    addIso639ToLanguageDetails("cat", "ca", "Catalan; Valencian")
    addIso639ToLanguageDetails("cau", null, "Caucasian languages")
    addIso639ToLanguageDetails("ceb", null, "Cebuano")
    addIso639ToLanguageDetails("cel", null, "Celtic languages")
    addIso639ToLanguageDetails("ces", "ch", "Czech")
    addIso639ToLanguageDetails("cha", "ch", "Chamorro")
    addIso639ToLanguageDetails("chb", null, "Chibcha")
    addIso639ToLanguageDetails("che", "ce", "Chechen")
    addIso639ToLanguageDetails("chg", null, "Chagatai")
    addIso639ToLanguageDetails("chi", "zh", "Chinese")
    addIso639ToLanguageDetails("chk", null, "Chuukese")
    addIso639ToLanguageDetails("chm", null, "Mari")
    addIso639ToLanguageDetails("chn", null, "Chinook jargon")
    addIso639ToLanguageDetails("cho", null, "Choctaw")
    addIso639ToLanguageDetails("chp", null, "Chipewyan; Dene Suline")
    addIso639ToLanguageDetails("chr", null, "Cherokee")
    addIso639ToLanguageDetails("chu", "cu", "Church Slavic; Old Slavonic; Church Slavonic; Old Bulgarian; Old Church Slavonic")
    addIso639ToLanguageDetails("chv", "cv", "Chuvash")
    addIso639ToLanguageDetails("chy", null, "Cheyenne")
    addIso639ToLanguageDetails("ckb", null, "Central Kurdish")
    addIso639ToLanguageDetails("ckl", null, "Kibaku") // Ethnologue.
    addIso639ToLanguageDetails("cky", null, "Cakfem-Mushere") // Ethnologue.
    addIso639ToLanguageDetails("cmc", null, "Chamic languages")
    addIso639ToLanguageDetails("cnr", null, "Montenegrin")
    addIso639ToLanguageDetails("cop", null, "Coptic")
    addIso639ToLanguageDetails("cor", "kw", "Cornish")
    addIso639ToLanguageDetails("cos", "co", "Corsican")
    addIso639ToLanguageDetails("cpe", null, "Creoles and pidgins, English based")
    addIso639ToLanguageDetails("cpf", null, "Creoles and pidgins, French-based")
    addIso639ToLanguageDetails("cpp", null, "Creoles and pidgins, Portuguese-based")
    addIso639ToLanguageDetails("cre", "cr", "Cree")
    addIso639ToLanguageDetails("crh", null, "Crimean Tatar; Crimean Turkish")
    addIso639ToLanguageDetails("crp", null, "Creoles and pidgins")
    addIso639ToLanguageDetails("csb", null, "Kashubian")
    addIso639ToLanguageDetails("cus", null, "Cushitic languages")
    addIso639ToLanguageDetails("cze", "cs", "Czech")
    addIso639ToLanguageDetails("dak", null, "Dakota")
    addIso639ToLanguageDetails("dan", "da", "Danish")
    addIso639ToLanguageDetails("dar", null, "Dargwa")
    addIso639ToLanguageDetails("day", null, "Land Dayak languages")
    addIso639ToLanguageDetails("del", null, "Delaware")
    addIso639ToLanguageDetails("den", null, "Slave (Athapascan)")
    addIso639ToLanguageDetails("deu", "de", "German")
    addIso639ToLanguageDetails("dgr", null, "Dogrib")
    addIso639ToLanguageDetails("din", null, "Dinka")
    addIso639ToLanguageDetails("div", "dv", "Divehi; Dhivehi; Maldivian")
    addIso639ToLanguageDetails("doi", null, "Dogri")
    addIso639ToLanguageDetails("dra", null, "Dravidian languages")
    addIso639ToLanguageDetails("dsb", null, "Lower Sorbian")
    addIso639ToLanguageDetails("dua", null, "Duala")
    addIso639ToLanguageDetails("dum", null, "Dutch, Middle (ca.1050-1350)")
    addIso639ToLanguageDetails("dut", "nl", "Dutch; Flemish")
    addIso639ToLanguageDetails("dyu", null, "Dyula")
    addIso639ToLanguageDetails("dzo", "dz", "Dzongkha")
    addIso639ToLanguageDetails("efi", null, "Efik")
    addIso639ToLanguageDetails("egy", null, "Egyptian (Ancient)")
    addIso639ToLanguageDetails("eka", null, "Ekajuk")
    addIso639ToLanguageDetails("ekk", null, "Standard Estonian")
    addIso639ToLanguageDetails("eky", null, "Kayah, Eastern") // Ethnologue.
    addIso639ToLanguageDetails("eky", null, "Kayah, Eastern") // Ethnologue.
    addIso639ToLanguageDetails("elx", null, "Elamite")
    addIso639ToLanguageDetails("eng", "en", "English")
    addIso639ToLanguageDetails("enm", null, "English, Middle (1100-1500)")
    addIso639ToLanguageDetails("epo", "eo", "Esperanto")
    addIso639ToLanguageDetails("est", "et", "Estonian")
    addIso639ToLanguageDetails("ewe", "ee", "Ewe")
    addIso639ToLanguageDetails("ewo", null, "Ewondo")
    addIso639ToLanguageDetails("fan", null, "Fang")
    addIso639ToLanguageDetails("fao", "fo", "Faroese")
    addIso639ToLanguageDetails("fat", null, "Fanti")
    addIso639ToLanguageDetails("fij", "fj", "Fijian")
    addIso639ToLanguageDetails("fil", null, "Filipino; Pilipino")
    addIso639ToLanguageDetails("fin", "fi", "Finnish")
    addIso639ToLanguageDetails("fiu", null, "Finno-Ugrian languages")
    addIso639ToLanguageDetails("fon", null, "Fon")
    addIso639ToLanguageDetails("fra", "fr", "French")
    addIso639ToLanguageDetails("fre", "fr", "French")
    addIso639ToLanguageDetails("frm", null, "French, Middle (ca.1400-1600)")
    addIso639ToLanguageDetails("fro", null, "French, Old (842-ca.1400)")
    addIso639ToLanguageDetails("frr", null, "Northern Frisian")
    addIso639ToLanguageDetails("frs", null, "Eastern Frisian")
    addIso639ToLanguageDetails("fry", "fy", "Western Frisian")
    addIso639ToLanguageDetails("ful", "ff", "Fulah")
    addIso639ToLanguageDetails("fur", null, "Friulian")
    addIso639ToLanguageDetails("gaa", null, "Ga")
    addIso639ToLanguageDetails("gay", null, "Gayo")
    addIso639ToLanguageDetails("gaz", null, "West Central Oromo")
    addIso639ToLanguageDetails("gba", null, "Gbaya")
    addIso639ToLanguageDetails("gbr", null, "Gbagyi")
    addIso639ToLanguageDetails("gek", null, "Ywom") // Ethnologue.
    addIso639ToLanguageDetails("gem", null, "Germanic languages")
    addIso639ToLanguageDetails("geo", "ka", "Georgian")
    addIso639ToLanguageDetails("ger", "de", "German")
    addIso639ToLanguageDetails("gez", null, "Geez")
    addIso639ToLanguageDetails("gil", null, "Gilbertese")
    addIso639ToLanguageDetails("gla", "gd", "Gaelic; Scottish Gaelic")
    addIso639ToLanguageDetails("gle", "ga", "Irish")
    addIso639ToLanguageDetails("glg", "gl", "Galician")
    addIso639ToLanguageDetails("glv", "gv", "Manx")
    addIso639ToLanguageDetails("gmh", null, "German, Middle High (ca.1050-1500)")
    addIso639ToLanguageDetails("goh", null, "German, Old High (ca.750-1050)")
    addIso639ToLanguageDetails("gon", null, "Gondi")
    addIso639ToLanguageDetails("gor", null, "Gorontalo")
    addIso639ToLanguageDetails("got", null, "Gothic")
    addIso639ToLanguageDetails("grb", null, "Grebo")
    addIso639ToLanguageDetails("grc", null, "Greek, Ancient (to 1453)")
    addIso639ToLanguageDetails("gre", "el", "Greek, Modern (1453-)")
    addIso639ToLanguageDetails("grn", "gn", "Guarani")
    addIso639ToLanguageDetails("gsw", null, "Swiss German; Alemannic; Alsatian")
    addIso639ToLanguageDetails("guj", "gu", "Gujarati")
    addIso639ToLanguageDetails("gwi", null, "Gwich'in")
    addIso639ToLanguageDetails("gyz", null, "Gyaazi") // Ethnologue.
    addIso639ToLanguageDetails("hai", null, "Haida")
    addIso639ToLanguageDetails("hat", "ht", "Haitian; Haitian Creole")
    addIso639ToLanguageDetails("hau", "ha", "Hausa")
    addIso639ToLanguageDetails("haw", null, "Hawaiian")
    addIso639ToLanguageDetails("heb", "he", "Hebrew")
    addIso639ToLanguageDetails("her", "hz", "Herero")
    addIso639ToLanguageDetails("hil", null, "Hiligaynon")
    addIso639ToLanguageDetails("him", null, "Himachali languages; Western Pahari languages")
    addIso639ToLanguageDetails("hin", "hi", "Hindi")
    addIso639ToLanguageDetails("hit", null, "Hittite")
    addIso639ToLanguageDetails("hmn", null, "Hmong; Mong")
    addIso639ToLanguageDetails("hne", null, "Chhattisgarhi")
    addIso639ToLanguageDetails("hmo", "ho", "Hiri Motu")
    addIso639ToLanguageDetails("hrv", "hr", "Croatian")
    addIso639ToLanguageDetails("hsb", null, "Upper Sorbian")
    addIso639ToLanguageDetails("hun", "hu", "Hungarian")
    addIso639ToLanguageDetails("hup", null, "Hupa")
    addIso639ToLanguageDetails("hwo", null, "Hwana") // Ethnologue.
    addIso639ToLanguageDetails("iba", null, "Iban")
    addIso639ToLanguageDetails("ibo", "ig", "Igbo")
    addIso639ToLanguageDetails("ice", "is", "Icelandic")
    addIso639ToLanguageDetails("ido", "io", "Ido")
    addIso639ToLanguageDetails("iii", "ii", "Sichuan Yi; Nuosu")
    addIso639ToLanguageDetails("ijo", null, "Ijo languages")
    addIso639ToLanguageDetails("iko", null, "Olulumo-Ikom") // Ethnologue.
    addIso639ToLanguageDetails("iku", "iu", "Inuktitut")
    addIso639ToLanguageDetails("ile", "ie", "Interlingue; Occidental")
    addIso639ToLanguageDetails("ilo", null, "Iloko")
    addIso639ToLanguageDetails("ina", "ia", "Interlingua (International Auxiliary Language Association)")
    addIso639ToLanguageDetails("inc", null, "Indic languages")
    addIso639ToLanguageDetails("ind", "id", "Indonesian")
    addIso639ToLanguageDetails("ine", null, "Indo-European languages")
    addIso639ToLanguageDetails("inh", null, "Ingush")
    addIso639ToLanguageDetails("ipk", "ik", "Inupiaq")
    addIso639ToLanguageDetails("ira", null, "Iranian languages")
    addIso639ToLanguageDetails("iro", null, "Iroquoian languages")
    addIso639ToLanguageDetails("isl", null, "Icelandic")
    addIso639ToLanguageDetails("ita", "it", "Italian")
    addIso639ToLanguageDetails("jav", "jv", "Javanese")
    addIso639ToLanguageDetails("jbo", null, "Lojban")
    addIso639ToLanguageDetails("jer", null, "Jere") // Ethnologue.
    addIso639ToLanguageDetails("jgk", null, "Gwak") // Ethnologue.
    addIso639ToLanguageDetails("jpn", "ja", "Japanese")
    addIso639ToLanguageDetails("jpr", null, "Judeo-Persian")
    addIso639ToLanguageDetails("jrb", null, "Judeo-Arabic")
    addIso639ToLanguageDetails("kaa", null, "Kara-Kalpak")
    addIso639ToLanguageDetails("kab", null, "Kabyle")
    addIso639ToLanguageDetails("kac", null, "Kachin; Jingpho")
    addIso639ToLanguageDetails("kal", "kl", "Kalaallisut; Greenlandic")
    addIso639ToLanguageDetails("kam", null, "Kamba")
    addIso639ToLanguageDetails("kan", "kn", "Kannada")
    addIso639ToLanguageDetails("kar", null, "Karen languages")
    addIso639ToLanguageDetails("kas", "ks", "Kashmiri")
    addIso639ToLanguageDetails("kau", "kr", "Kanuri")
    addIso639ToLanguageDetails("kaw", null, "Kawi")
    addIso639ToLanguageDetails("kaz", "kk", "Kazakh")
    addIso639ToLanguageDetails("kbd", null, "Kabardian")
    addIso639ToLanguageDetails("kha", null, "Khasi")
    addIso639ToLanguageDetails("khi", null, "Khoisan languages")
    addIso639ToLanguageDetails("khm", "km", "Central Khmer")
    addIso639ToLanguageDetails("kho", null, "Khotanese; Sakan")
    addIso639ToLanguageDetails("kik", "ki", "Kikuyu; Gikuyu")
    addIso639ToLanguageDetails("kin", "rw", "Kinyarwanda")
    addIso639ToLanguageDetails("kir", "ky", "Kirghiz; Kyrgyz")
    addIso639ToLanguageDetails("kji", null, "Kanaba")
    addIso639ToLanguageDetails("kmb", null, "Kimbundu")
    addIso639ToLanguageDetails("kok", null, "Konkani")
    addIso639ToLanguageDetails("kom", "kv", "Komi")
    addIso639ToLanguageDetails("kon", "kg", "Kongo")
    addIso639ToLanguageDetails("koq", null, "Kota") // Ethnologue.
    addIso639ToLanguageDetails("kor", "ko", "Korean")
    addIso639ToLanguageDetails("kos", null, "Kosraean")
    addIso639ToLanguageDetails("kpe", null, "Kpelle")
    addIso639ToLanguageDetails("krc", null, "Karachay-Balkar")
    addIso639ToLanguageDetails("krl", null, "Karelian")
    addIso639ToLanguageDetails("kro", null, "Kru languages")
    addIso639ToLanguageDetails("kru", null, "Kurukh")
    addIso639ToLanguageDetails("kua", "kj", "Kuanyama; Kwanyama")
    addIso639ToLanguageDetails("kum", null, "Kumyk")
    addIso639ToLanguageDetails("kur", "ku", "Kurdish")
    addIso639ToLanguageDetails("kut", null, "Kutenai")
    addIso639ToLanguageDetails("kvy", null, "Yintale") // Ethnologue.
    addIso639ToLanguageDetails("kxf", null, "Kawyaw") // Ethnologue.
    addIso639ToLanguageDetails("kxf", null, "Kawyaw") // Ethnologue.
    addIso639ToLanguageDetails("lad", null, "Ladino")
    addIso639ToLanguageDetails("lah", null, "Lahnda")
    addIso639ToLanguageDetails("lam", null, "Lamba")
    addIso639ToLanguageDetails("lao", "lo", "Lao")
    addIso639ToLanguageDetails("lat", "la", "Latin")
    addIso639ToLanguageDetails("lav", "lv", "Latvian")
    addIso639ToLanguageDetails("lez", null, "Lezghian")
    addIso639ToLanguageDetails("lim", "li", "Limburgan; Limburger; Limburgish")
    addIso639ToLanguageDetails("lin", "ln", "Lingala")
    addIso639ToLanguageDetails("lit", "lt", "Lithuanian")
    addIso639ToLanguageDetails("lla", null, "Lala-Roba") // Ethnologue.
    addIso639ToLanguageDetails("lol", null, "Mongo")
    addIso639ToLanguageDetails("loz", null, "Lozi")
    addIso639ToLanguageDetails("ltz", "lb", "Luxembourgish; Letzeburgesch")
    addIso639ToLanguageDetails("lua", null, "Luba-Lulua")
    addIso639ToLanguageDetails("lub", "lu", "Luba-Katanga")
    addIso639ToLanguageDetails("lug", "lg", "Ganda")
    addIso639ToLanguageDetails("lui", null, "Luiseno")
    addIso639ToLanguageDetails("lun", null, "Lunda")
    addIso639ToLanguageDetails("luo", null, "Luo (Kenya and Tanzania)")
    addIso639ToLanguageDetails("lus", null, "Lushai")
    addIso639ToLanguageDetails("mac", "mk", "Macedonian")
    addIso639ToLanguageDetails("mad", null, "Madurese")
    addIso639ToLanguageDetails("mag", null, "Magahi")
    addIso639ToLanguageDetails("mah", "mh", "Marshallese")
    addIso639ToLanguageDetails("mai", null, "Maithili")
    addIso639ToLanguageDetails("mak", null, "Makasar")
    addIso639ToLanguageDetails("mal", "ml", "Malayalam")
    addIso639ToLanguageDetails("man", null, "Mandingo")
    addIso639ToLanguageDetails("mao", "mi", "Maori")
    addIso639ToLanguageDetails("map", null, "Austronesian languages")
    addIso639ToLanguageDetails("mar", "mr", "Marathi")
    addIso639ToLanguageDetails("mas", null, "Masai")
    addIso639ToLanguageDetails("may", "ms", "Malay")
    addIso639ToLanguageDetails("mdf", null, "Moksha")
    addIso639ToLanguageDetails("mdr", null, "Mandar")
    addIso639ToLanguageDetails("mdt", null, "Mbere") // Ethnologue.
    addIso639ToLanguageDetails("men", null, "Mende")
    addIso639ToLanguageDetails("mfn", null, "Mbembe, Cross River") // Ethnologue.
    addIso639ToLanguageDetails("mga", null, "Irish, Middle (900-1200)")
    addIso639ToLanguageDetails("mgj", null, "Abureni") // Ethnologue.
    addIso639ToLanguageDetails("mho", null, "Mashi") // Ethnologue.
    addIso639ToLanguageDetails("mic", null, "Mi'kmaq; Micmac")
    addIso639ToLanguageDetails("min", null, "Minangkabau")
    addIso639ToLanguageDetails("mis", null, "Uncoded languages")
    addIso639ToLanguageDetails("mkf", null, "Vune mi") // Ethnologue.
    addIso639ToLanguageDetails("mkh", null, "Mon-Khmer languages")
    addIso639ToLanguageDetails("mlg", "mg", "Malagasy")
    addIso639ToLanguageDetails("mlt", "mt", "Maltese")
    addIso639ToLanguageDetails("mnc", null, "Manchu")
    addIso639ToLanguageDetails("mni", null, "Manipuri")
    addIso639ToLanguageDetails("mno", null, "Manobo languages")
    addIso639ToLanguageDetails("moh", null, "Mohawk")
    addIso639ToLanguageDetails("mon", "mn", "Mongolian")
    addIso639ToLanguageDetails("mos", null, "Mossi")
    addIso639ToLanguageDetails("mul", null, "Multiple languages")
    addIso639ToLanguageDetails("mun", null, "Munda languages")
    addIso639ToLanguageDetails("mus", null, "Creek")
    addIso639ToLanguageDetails("mwl", null, "Mirandese")
    addIso639ToLanguageDetails("mwr", null, "Marwari")
    addIso639ToLanguageDetails("myn", null, "Mayan languages")
    addIso639ToLanguageDetails("myv", null, "Erzya")
    addIso639ToLanguageDetails("nah", null, "Nahuatl languages")
    addIso639ToLanguageDetails("nai", null, "North American Indian languages")
    addIso639ToLanguageDetails("nap", null, "Neapolitan")
    addIso639ToLanguageDetails("nau", "na", "Nauru")
    addIso639ToLanguageDetails("nav", "nv", "Navajo; Navaho")
    addIso639ToLanguageDetails("nbl", "nr", "Ndebele, South; South Ndebele")
    addIso639ToLanguageDetails("ndd", null, "Nde-Nsele-Nta") // Ethnologue.
    addIso639ToLanguageDetails("nde", "nd", "Ndebele, North; North Ndebele")
    addIso639ToLanguageDetails("ndo", "ng", "Ndonga")
    addIso639ToLanguageDetails("nds", null, "Low German; Low Saxon; German, Low; Saxon, Low")
    addIso639ToLanguageDetails("nep", "ne", "Nepali")
    addIso639ToLanguageDetails("new", null, "Nepal Bhasa; Newari")
    addIso639ToLanguageDetails("nia", null, "Nias")
    addIso639ToLanguageDetails("nic", null, "Niger-Kordofanian languages")
    addIso639ToLanguageDetails("niu", null, "Niuean")
    addIso639ToLanguageDetails("nno", "nn", "Norwegian Nynorsk; Nynorsk, Norwegian")
    addIso639ToLanguageDetails("nob", "nb", "Bokmål, Norwegian; Norwegian Bokmål")
    addIso639ToLanguageDetails("nog", null, "Nogai")
    addIso639ToLanguageDetails("non", null, "Norse, Old")
    addIso639ToLanguageDetails("nor", "no", "Norwegian")
    addIso639ToLanguageDetails("nqo", null, "N'Ko")
    addIso639ToLanguageDetails("nso", null, "Pedi; Sepedi; Northern Sotho")
    addIso639ToLanguageDetails("nub", null, "Nubian languages")
    addIso639ToLanguageDetails("nwc", null, "Classical Newari; Old Newari; Classical Nepal Bhasa")
    addIso639ToLanguageDetails("nya", "ny", "Chichewa; Chewa; Nyanja")
    addIso639ToLanguageDetails("nym", null, "Nyamwezi")
    addIso639ToLanguageDetails("nyn", null, "Nyankole")
    addIso639ToLanguageDetails("nyo", null, "Nyoro")
    addIso639ToLanguageDetails("nzb", null, "Njebi") // Ethnologue.
    addIso639ToLanguageDetails("nzi", null, "Nzima")
    addIso639ToLanguageDetails("oci", "oc", "Occitan (post 1500)")
    addIso639ToLanguageDetails("ofu", null, "Efutop") // Ethnologue.
    addIso639ToLanguageDetails("oji", "oj", "Ojibwa")
    addIso639ToLanguageDetails("ori", "or", "Oriya")
    addIso639ToLanguageDetails("orm", "om", "Oromo")
    addIso639ToLanguageDetails("osa", null, "Osage")
    addIso639ToLanguageDetails("oss", "os", "Ossetian; Ossetic")
    addIso639ToLanguageDetails("ota", null, "Turkish, Ottoman (1500-1928)")
    addIso639ToLanguageDetails("oto", null, "Otomian languages")
    addIso639ToLanguageDetails("paa", null, "Papuan languages")
    addIso639ToLanguageDetails("pag", null, "Pangasinan")
    addIso639ToLanguageDetails("pal", null, "Pahlavi")
    addIso639ToLanguageDetails("pam", null, "Pampanga; Kapampangan")
    addIso639ToLanguageDetails("pan", "pa", "Panjabi; Punjabi")
    addIso639ToLanguageDetails("pap", null, "Papiamento")
    addIso639ToLanguageDetails("pau", null, "Palauan")
    addIso639ToLanguageDetails("peo", null, "Persian, Old (ca.600-400 B.C.)")
    addIso639ToLanguageDetails("per", "fa", "Persian")
    addIso639ToLanguageDetails("pes", null, "Persian, Iranian")
    addIso639ToLanguageDetails("phi", null, "Philippine languages")
    addIso639ToLanguageDetails("phn", null, "Phoenician")
    addIso639ToLanguageDetails("pli", "pi", "Pali")
    addIso639ToLanguageDetails("plj", null, "Polci")
    addIso639ToLanguageDetails("pol", "pl", "Polish")
    addIso639ToLanguageDetails("pon", null, "Pohnpeian")
    addIso639ToLanguageDetails("por", "pt", "Portuguese")
    addIso639ToLanguageDetails("pra", null, "Prakrit languages")
    addIso639ToLanguageDetails("pro", null, "Provençal, Old (to 1500); Occitan, Old (to 1500)")
    addIso639ToLanguageDetails("pus", "ps", "Pushto; Pashto")
    addIso639ToLanguageDetails("pym", null, "Pyam") // Ethnologue.
    addIso639ToLanguageDetails("qaa-qtz", null, "Reserved for local use")
    addIso639ToLanguageDetails("que", "qu", "Quechua")
    addIso639ToLanguageDetails("raj", null, "Rajasthani")
    addIso639ToLanguageDetails("rap", null, "Rapanui")
    addIso639ToLanguageDetails("rar", null, "Rarotongan; Cook Islands Maori")
    addIso639ToLanguageDetails("rmz", null, "Marma") // Ethnologue.
    addIso639ToLanguageDetails("roa", null, "Romance languages")
    addIso639ToLanguageDetails("roh", "rm", "Romansh")
    addIso639ToLanguageDetails("rom", null, "Romany")
    addIso639ToLanguageDetails("ron", null, "Romanian")
    addIso639ToLanguageDetails("rum", "ro", "Romanian; Moldavian; Moldovan")
    addIso639ToLanguageDetails("run", "rn", "Rundi")
    addIso639ToLanguageDetails("rup", null, "Aromanian; Arumanian; Macedo-Romanian")
    addIso639ToLanguageDetails("rus", "ru", "Russian")
    addIso639ToLanguageDetails("sad", null, "Sandawe")
    addIso639ToLanguageDetails("sag", "sg", "Sango")
    addIso639ToLanguageDetails("sah", null, "Yakut")
    addIso639ToLanguageDetails("sai", null, "South American Indian languages")
    addIso639ToLanguageDetails("sal", null, "Salishan languages")
    addIso639ToLanguageDetails("sam", null, "Samaritan Aramaic")
    addIso639ToLanguageDetails("san", "sa", "Sanskrit")
    addIso639ToLanguageDetails("sas", null, "Sasak")
    addIso639ToLanguageDetails("sat", null, "Santali")
    addIso639ToLanguageDetails("scn", null, "Sicilian")
    addIso639ToLanguageDetails("sco", null, "Scots")
    addIso639ToLanguageDetails("sel", null, "Selkup")
    addIso639ToLanguageDetails("sem", null, "Semitic languages")
    addIso639ToLanguageDetails("sga", null, "Irish, Old (to 900)")
    addIso639ToLanguageDetails("sgn", null, "Sign Languages")
    addIso639ToLanguageDetails("shn", null, "Shan")
    addIso639ToLanguageDetails("sid", null, "Sidamo")
    addIso639ToLanguageDetails("sin", "si", "Sinhala; Sinhalese")
    addIso639ToLanguageDetails("sio", null, "Siouan languages")
    addIso639ToLanguageDetails("sit", null, "Sino-Tibetan languages")
    addIso639ToLanguageDetails("sla", null, "Slavic languages")
    addIso639ToLanguageDetails("slk", "sk", "Slovak")
    addIso639ToLanguageDetails("slo", "sk", "Slovak")
    addIso639ToLanguageDetails("slv", "sl", "Slovenian")
    addIso639ToLanguageDetails("sma", null, "Southern Sami")
    addIso639ToLanguageDetails("sme", "se", "Northern Sami")
    addIso639ToLanguageDetails("smi", null, "Sami languages")
    addIso639ToLanguageDetails("smj", null, "Lule Sami")
    addIso639ToLanguageDetails("smn", null, "Inari Sami")
    addIso639ToLanguageDetails("smo", "sm", "Samoan")
    addIso639ToLanguageDetails("sms", null, "Skolt Sami")
    addIso639ToLanguageDetails("sna", "sn", "Shona")
    addIso639ToLanguageDetails("snd", "sd", "Sindhi")
    addIso639ToLanguageDetails("snk", null, "Soninke")
    addIso639ToLanguageDetails("snq", null, "Sangu") // Ethnologue.
    addIso639ToLanguageDetails("sog", null, "Sogdian")
    addIso639ToLanguageDetails("som", "so", "Somali")
    addIso639ToLanguageDetails("son", null, "Songhai languages")
    addIso639ToLanguageDetails("sot", "st", "Sotho, Southern")
    addIso639ToLanguageDetails("spa", "es", "Spanish; Castilian")
    addIso639ToLanguageDetails("srd", "sc", "Sardinian")
    addIso639ToLanguageDetails("srn", null, "Sranan Tongo")
    addIso639ToLanguageDetails("srp", "sr", "Serbian")
    addIso639ToLanguageDetails("srr", null, "Serer")
    addIso639ToLanguageDetails("ssa", null, "Nilo-Saharan languages")
    addIso639ToLanguageDetails("ssw", "ss", "Swati")
    addIso639ToLanguageDetails("suk", null, "Sukuma")
    addIso639ToLanguageDetails("sun", "su", "Sundanese")
    addIso639ToLanguageDetails("suq", null, "Tirmaga-Chai Suri") // Ethnologue.
    addIso639ToLanguageDetails("sus", null, "Susu")
    addIso639ToLanguageDetails("sux", null, "Sumerian")
    addIso639ToLanguageDetails("swa", "sw", "Swahili")
    addIso639ToLanguageDetails("swe", "sv", "Swedish")
    addIso639ToLanguageDetails("swh", null, "Swahili") // Ethnologue.
    addIso639ToLanguageDetails("swj", null, "Sira") // Ethnologue.
    addIso639ToLanguageDetails("syc", null, "Classical Syriac")
    addIso639ToLanguageDetails("syr", null, "Syriac")
    addIso639ToLanguageDetails("tah", "ty", "Tahitian")
    addIso639ToLanguageDetails("tai", null, "Tai languages")
    addIso639ToLanguageDetails("tam", "ta", "Tamil")
    addIso639ToLanguageDetails("tat", "tt", "Tatar")
    addIso639ToLanguageDetails("tel", "te", "Telugu")
    addIso639ToLanguageDetails("tem", null, "Timne")
    addIso639ToLanguageDetails("ter", null, "Tereno")
    addIso639ToLanguageDetails("tet", null, "Tetum")
    addIso639ToLanguageDetails("tgk", "tg", "Tajik")
    addIso639ToLanguageDetails("tgl", "tl", "Tagalog")
    addIso639ToLanguageDetails("tha", "th", "Thai")
    addIso639ToLanguageDetails("tib", "bo", "Tibetan")
    addIso639ToLanguageDetails("tig", null, "Tigre")
    addIso639ToLanguageDetails("tir", "ti", "Tigrinya")
    addIso639ToLanguageDetails("tiv", null, "Tiv")
    addIso639ToLanguageDetails("tkl", null, "Tokelau")
    addIso639ToLanguageDetails("tlh", null, "Klingon; tlhIngan-Hol")
    addIso639ToLanguageDetails("tli", null, "Tlingit")
    addIso639ToLanguageDetails("tmf", null, "Toba-Maskoy") // Ethnologue.
    addIso639ToLanguageDetails("tmh", null, "Tamashek")
    addIso639ToLanguageDetails("tog", null, "Tonga (Nyasa)")
    addIso639ToLanguageDetails("ton", "to", "Tonga (Tonga Islands)")
    addIso639ToLanguageDetails("tpi", null, "Tok Pisin")
    addIso639ToLanguageDetails("tpj", null, "Ñandeva") // Ethnologue.
    addIso639ToLanguageDetails("tsi", null, "Tsimshian")
    addIso639ToLanguageDetails("tsn", "tn", "Tswana")
    addIso639ToLanguageDetails("tso", "ts", "Tsonga")
    addIso639ToLanguageDetails("tuk", "tk", "Turkmen")
    addIso639ToLanguageDetails("tum", null, "Tumbuka")
    addIso639ToLanguageDetails("tup", null, "Tupi languages")
    addIso639ToLanguageDetails("tur", "tr", "Turkish")
    addIso639ToLanguageDetails("tut", null, "Altaic languages")
    addIso639ToLanguageDetails("tvl", null, "Tuvalu")
    addIso639ToLanguageDetails("twi", "tw", "Twi")
    addIso639ToLanguageDetails("tyv", null, "Tuvinian")
    addIso639ToLanguageDetails("tyy", null, "Tiyaa") // Ethnologue.
    addIso639ToLanguageDetails("udm", null, "Udmurt")
    addIso639ToLanguageDetails("uga", null, "Ugaritic")
    addIso639ToLanguageDetails("uig", "ug", "Uighur; Uyghur")
    addIso639ToLanguageDetails("ukr", "uk", "Ukrainian")
    addIso639ToLanguageDetails("umb", null, "Umbundu")
    addIso639ToLanguageDetails("und", null, "Undetermined")
    addIso639ToLanguageDetails("urd", "ur", "Urdu")
    addIso639ToLanguageDetails("uzb", "uz", "Uzbek")
    addIso639ToLanguageDetails("vai", null, "Vai")
    addIso639ToLanguageDetails("ven", "ve", "Venda")
    addIso639ToLanguageDetails("vie", "vi", "Vietnamese")
    addIso639ToLanguageDetails("vol", "vo", "Volapük")
    addIso639ToLanguageDetails("vot", null, "Votic")
    addIso639ToLanguageDetails("wak", null, "Wakashan languages")
    addIso639ToLanguageDetails("wal", null, "Wolaitta; Wolaytta")
    addIso639ToLanguageDetails("war", null, "Waray")
    addIso639ToLanguageDetails("was", null, "Washo")
    addIso639ToLanguageDetails("wel", "cy", "Welsh")
    addIso639ToLanguageDetails("wen", null, "Sorbian languages")
    addIso639ToLanguageDetails("wji", null, "Warji") // Ethnologue.
    addIso639ToLanguageDetails("wln", "wa", "Walloon")
    addIso639ToLanguageDetails("wol", "wo", "Wolof")
    addIso639ToLanguageDetails("wum", null, "Wumbvu") // Ethnologue.
    addIso639ToLanguageDetails("xal", null, "Kalmyk; Oirat")
    addIso639ToLanguageDetails("xho", "xh", "Xhosa")
    addIso639ToLanguageDetails("xsn", null, "Sanga") // Ethnologue.
    addIso639ToLanguageDetails("xte", null, "Ketengban") // Ethnologue.
    addIso639ToLanguageDetails("yao", null, "Yao")
    addIso639ToLanguageDetails("yap", null, "Yapese")
    addIso639ToLanguageDetails("yid", "yi", "Yiddish")
    addIso639ToLanguageDetails("yom", null, "Kiyombe")
    addIso639ToLanguageDetails("yor", "yo", "Yoruba")
    addIso639ToLanguageDetails("ypk", null, "Yupik languages")
    addIso639ToLanguageDetails("zap", null, "Zapotec")
    addIso639ToLanguageDetails("zbl", null, "Blissymbols; Blissymbolics; Bliss")
    addIso639ToLanguageDetails("zen", null, "Zenaga")
    addIso639ToLanguageDetails("zgh", null, "Standard Moroccan Tamazight")
    addIso639ToLanguageDetails("zha", "za", "Zhuang; Chuang")
    addIso639ToLanguageDetails("zmb", null, "Zimba") // Ethnologue.
    addIso639ToLanguageDetails("znd", null, "Zande languages")
    addIso639ToLanguageDetails("zul", "zu", "Zulu")
    addIso639ToLanguageDetails("zun", null, "Zuni")
    addIso639ToLanguageDetails("zxx", null, "No linguistic content; Not applicable")
    addIso639ToLanguageDetails("zza", null, "Zaza; Dimili; Dimli; Kirdki; Kirmanjki; Zazaki")
  }
}