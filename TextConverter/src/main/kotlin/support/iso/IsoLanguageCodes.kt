package org.stepbible.textconverter.support.iso


/******************************************************************************/
/**
 * Information related to ISO language codes.
 *
 * Two character codes come from ISO 639-1.
 *
 * Three character codes come from ISO 639-3.
 *
 * I ignore ISO 639-2/T and ISO 639/T because they seem to serve only to
 * confuse, without contributing anything.
 *
 * I do not include the full list of ISO 690-3 codes, since it runs to over
 * 8000.  (I therefore implicitly assume that any 3-character code I am asked
 * to handle is ok, because I may well not have the information needed to
 * check it.)
 *
 * In fact, the ISO 639 standards are no longer the latest and greatest.  In
 * theory, we should be working to the list in [IETF spec BCP 47](https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry),
 * which has a unique code for each language (2-character codes for some, and
 * 3-character codes for others).  In fact, these can be derived readily from
 * the existing ISO 639 codes if you simply use 2-character codes in preference
 * to 3-character codes where the former are available.
 *
 * The processing is driven by existing needs in this respect, and therefore
 * may follow this rule on some texts and not on others.
 *
 * **CAUTION**: If you are mapping from 3-character codes to 2-character codes,
 * be aware that in a few cases, two different 3-character codes map to the
 * same 2-character code.
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
     * Given a language code, returns the equivalent two character code.
     *
     * @param theLanguageCode What it says on the tin.
     * @return Two character code.
     */

    fun get2CharacterIsoCode (theLanguageCode: String): String
    {
        val languageCode = theLanguageCode.lowercase()
        return if (2 == languageCode.length)
          languageCode
        else if (null != m_3To2[theLanguageCode])
          m_3To2[theLanguageCode]!!.twoCharacterCode
        else
          languageCode
    }


    /****************************************************************************/
    /**
     * Given a language code, returns the equivalent three character code.
     *
     * @param theLanguageCode What it says on the tin.
     * @return Three character code.
     * */

    fun get3CharacterIsoCode (theLanguageCode: String): String
    {
        val languageCode = theLanguageCode.lowercase()
        if (2 == languageCode.length) return m_2To3[theLanguageCode]!!
        return languageCode.split("-")[0] // Assume 3-character codes do not need translation.  Some codes are longer, because they contain dashes.  Can't recall why.
    }


    /****************************************************************************/
    /**
     * Given a three character code, returns the language name.
     *
     * @param threeCharacterCode What it says on the tin.
     * @return Language name.
     */

    fun getLanguageName (threeCharacterCode: String): String
    {
        return m_3To2[threeCharacterCode]!!.name
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Private                                 **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    private data class Length2AndName (val twoCharacterCode: String, val name: String)
    private val m_2To3: MutableMap<String, String> = HashMap()
    private val m_3To2: Map<String, Length2AndName> = mapOf (
        "aar" to Length2AndName("aa", "Afar"),
        "abk" to Length2AndName("ab", "Abkhazian"),
        "ave" to Length2AndName("ae", "Avestan"),
        "afr" to Length2AndName("af", "Afrikaans"),
        "aka" to Length2AndName("ak", "Akan"),
        "amh" to Length2AndName("am", "Amharic"),
        "arg" to Length2AndName("an", "Aragonese"),
        "ara" to Length2AndName("ar", "Arabic"),
        "arb" to Length2AndName("ar", "Standard Arabic"),
        "asm" to Length2AndName("as", "Assamese"),
        "ava" to Length2AndName("av", "Avaric"),
        "aym" to Length2AndName("ay", "Aymara"),
        "aze" to Length2AndName("az", "Azerbaijani"),
        "bak" to Length2AndName("ba", "Bashkir"),
        "bel" to Length2AndName("be", "Belarusian"),
        "bul" to Length2AndName("bg", "Bulgarian"),
        "bih" to Length2AndName("bh", "Bihari languages"),
        "bis" to Length2AndName("bi", "Bislama"),
        "bam" to Length2AndName("bm", "Bambara"),
        "ben" to Length2AndName("bn", "Bengali"),
        "tib" to Length2AndName("bo", "Tibetan"),
        "bre" to Length2AndName("br", "Breton"),
        "bos" to Length2AndName("bs", "Bosnian"),
        "cat" to Length2AndName("ca", "Catalan, Valencian"),
        "che" to Length2AndName("ce", "Chechen"),
        "cha" to Length2AndName("ch", "Chamorro"),
        "cos" to Length2AndName("co", "Corsican"),
        "cre" to Length2AndName("cr", "Cree"),
        "ces" to Length2AndName("cs", "Czech"),
        "chu" to Length2AndName("cu", "Church Slavic, Church Slavonic, Old Bulgarian, Old Church Slavonic, Old Slavonic"),
        "chv" to Length2AndName("cv", "Chuvash"),
        "wel" to Length2AndName("cy", "Welsh"),
        "dan" to Length2AndName("da", "Danish"),
        "deu" to Length2AndName("de", "German"),
        "div" to Length2AndName("dv", "Dhivehi, Divehi, Maldivian"),
        "dzo" to Length2AndName("dz", "Dzongkha"),
        "ewe" to Length2AndName("ee", "Ewe"),
        "gre" to Length2AndName("el", "Modern Greek (1453-)"),
        "eng" to Length2AndName("en", "English"),
        "epo" to Length2AndName("eo", "Esperanto"),
        "spa" to Length2AndName("es", "Castilian, Spanish"),
        "est" to Length2AndName("et", "Estonian"),
        "baq" to Length2AndName("eu", "Basque"),
        "per" to Length2AndName("fa", "Persian"),
        "ful" to Length2AndName("ff", "Fulah"),
        "fin" to Length2AndName("fi", "Finnish"),
        "fij" to Length2AndName("fj", "Fijian"),
        "fao" to Length2AndName("fo", "Faroese"),
        "fra" to Length2AndName("fr", "French"),
        "fry" to Length2AndName("fy", "Western Frisian"),
        "gle" to Length2AndName("ga", "Irish"),
        "gla" to Length2AndName("gd", "Gaelic, Scottish Gaelic"),
        "glg" to Length2AndName("gl", "Galician"),
        "grc" to Length2AndName("grc", "Ancient Greek to 1453"),
        "grn" to Length2AndName("gn", "Guarani"),
        "guj" to Length2AndName("gu", "Gujarati"),
        "glv" to Length2AndName("gv", "Manx"),
        "hau" to Length2AndName("ha", "Hausa"),
        "heb" to Length2AndName("he", "Hebrew"),
        "hin" to Length2AndName("hi", "Hindi"),
        "hmo" to Length2AndName("ho", "Hiri Motu"),
        "hrv" to Length2AndName("hr", "Croatian"),
        "hat" to Length2AndName("ht", "Haitian, Haitian Creole"),
        "hun" to Length2AndName("hu", "Hungarian"),
        "arm" to Length2AndName("hy", "Armenian"),
        "her" to Length2AndName("hz", "Herero"),
        "ina" to Length2AndName("ia", "Interlingua (International Auxiliary Language Association)"),
        "ind" to Length2AndName("id", "Indonesian"),
        "ile" to Length2AndName("ie", "Interlingue, Occidental"),
        "ibo" to Length2AndName("ig", "Igbo"),
        "iii" to Length2AndName("ii", "Nuosu, Sichuan Yi"),
        "ipk" to Length2AndName("ik", "Inupiaq"),
        "ido" to Length2AndName("io", "Ido"),
        "ice" to Length2AndName("is", "Icelandic"),
        "ita" to Length2AndName("it", "Italian"),
        "iku" to Length2AndName("iu", "Inuktitut"),
        "jpn" to Length2AndName("ja", "Japanese"),
        "jav" to Length2AndName("jv", "Javanese"),
        "geo" to Length2AndName("ka", "Georgian"),
        "kon" to Length2AndName("kg", "Kongo"),
        "kik" to Length2AndName("ki", "Gikuyu, Kikuyu"),
        "kua" to Length2AndName("kj", "Kuanyama, Kwanyama"),
        "kaz" to Length2AndName("kk", "Kazakh"),
        "kal" to Length2AndName("kl", "Greenlandic, Kalaallisut"),
        "khm" to Length2AndName("km", "Central Khmer, Khmer"),
        "kan" to Length2AndName("kn", "Kannada"),
        "kor" to Length2AndName("ko", "Korean"),
        "kau" to Length2AndName("kr", "Kanuri"),
        "kas" to Length2AndName("ks", "Kashmiri"),
        "kur" to Length2AndName("ku", "Kurdish"),
        "kom" to Length2AndName("kv", "Komi"),
        "cor" to Length2AndName("kw", "Cornish"),
        "kir" to Length2AndName("ky", "Kirghiz, Kyrgyz"),
        "lat" to Length2AndName("la", "Latin"),
        "ltz" to Length2AndName("lb", "Letzeburgesch, Luxembourgish"),
        "lug" to Length2AndName("lg", "Ganda"),
        "lim" to Length2AndName("li", "Limburgan, Limburger, Limburgish"),
        "lin" to Length2AndName("ln", "Lingala"),
        "lao" to Length2AndName("lo", "Lao"),
        "lit" to Length2AndName("lt", "Lithuanian"),
        "lub" to Length2AndName("lu", "Luba-Katanga"),
        "lav" to Length2AndName("lv", "Latvian"),
        "mlg" to Length2AndName("mg", "Malagasy"),
        "mah" to Length2AndName("mh", "Marshallese"),
        "mao" to Length2AndName("mi", "Maori"),
        "mac" to Length2AndName("mk", "Macedonian"),
        "mal" to Length2AndName("ml", "Malayalam"),
        "mon" to Length2AndName("mn", "Mongolian"),
        "mar" to Length2AndName("mr", "Marathi"),
        "may" to Length2AndName("ms", "Malay (macrolanguage)"),
        "mlt" to Length2AndName("mt", "Maltese"),
        "bur" to Length2AndName("my", "Burmese"),
        "nau" to Length2AndName("na", "Nauru"),
        "nob" to Length2AndName("nb", "Norwegian Bokmål"),
        "nde" to Length2AndName("nd", "North Ndebele"),
        "nep" to Length2AndName("ne", "Nepali (macrolanguage)"),
        "ndo" to Length2AndName("ng", "Ndonga"),
        "nld" to Length2AndName("nl", "Dutch, Flemish"),
        "nno" to Length2AndName("nn", "Norwegian Nynorsk"),
        "nor" to Length2AndName("no", "Norwegian"),
        "nbl" to Length2AndName("nr", "South Ndebele"),
        "nav" to Length2AndName("nv", "Navaho, Navajo"),
        "nya" to Length2AndName("ny", "Chewa, Chichewa, Nyanja"),
        "oci" to Length2AndName("oc", "Occitan (post 1500)"),
        "oji" to Length2AndName("oj", "Ojibwa"),
        "orm" to Length2AndName("om", "Oromo"),
        "ori" to Length2AndName("or", "Oriya (macrolanguage)"),
        "oss" to Length2AndName("os", "Ossetian, Ossetic"),
        "pan" to Length2AndName("pa", "Panjabi, Punjabi"),
        "pli" to Length2AndName("pi", "Pali"),
        "pol" to Length2AndName("pl", "Polish"),
        "pus" to Length2AndName("ps", "Pashto, Pushto"),
        "por" to Length2AndName("pt", "Portuguese"),
        "que" to Length2AndName("qu", "Quechua"),
        "roh" to Length2AndName("rm", "Romansh"),
        "run" to Length2AndName("rn", "Rundi"),
        "ron" to Length2AndName("ro", "Romanian"),
        "rum" to Length2AndName("ro", "Moldavian, Moldovan, Romanian"),
        "rus" to Length2AndName("ru", "Russian"),
        "kin" to Length2AndName("rw", "Kinyarwanda"),
        "san" to Length2AndName("sa", "Sanskrit"),
        "srd" to Length2AndName("sc", "Sardinian"),
        "snd" to Length2AndName("sd", "Sindhi"),
        "sme" to Length2AndName("se", "Northern Sami"),
        "sag" to Length2AndName("sg", "Sango"),
        "hbs" to Length2AndName("sh", "Serbo-Croatian"),
        "sin" to Length2AndName("si", "Sinhala, Sinhalese"),
        "slo" to Length2AndName("sk", "Slovak"),
        "slv" to Length2AndName("sl", "Slovenian"),
        "smo" to Length2AndName("sm", "Samoan"),
        "sna" to Length2AndName("sn", "Shona"),
        "som" to Length2AndName("so", "Somali"),
        "alb" to Length2AndName("sq", "Albanian"),
        "srp" to Length2AndName("sr", "Serbian"),
        "ssw" to Length2AndName("ss", "Swati"),
        "sot" to Length2AndName("st", "Southern Sotho"),
        "sun" to Length2AndName("su", "Sundanese"),
        "swe" to Length2AndName("sv", "Swedish"),
        "swa" to Length2AndName("sw", "Swahili (macrolanguage)"),
        "tam" to Length2AndName("ta", "Tamil"),
        "tel" to Length2AndName("te", "Telugu"),
        "tgk" to Length2AndName("tg", "Tajik"),
        "tha" to Length2AndName("th", "Thai"),
        "tir" to Length2AndName("ti", "Tigrinya"),
        "tuk" to Length2AndName("tk", "Turkmen"),
        "tgl" to Length2AndName("tl", "Tagalog"),
        "tsn" to Length2AndName("tn", "Tswana"),
        "ton" to Length2AndName("to", "Tonga (Tonga Islands)"),
        "tur" to Length2AndName("tr", "Turkish"),
        "tso" to Length2AndName("ts", "Tsonga"),
        "tat" to Length2AndName("tt", "Tatar"),
        "twi" to Length2AndName("tw", "Twi"),
        "tah" to Length2AndName("ty", "Tahitian"),
        "uig" to Length2AndName("ug", "Uighur, Uyghur"),
        "ukr" to Length2AndName("uk", "Ukrainian"),
        "urd" to Length2AndName("ur", "Urdu"),
        "uzb" to Length2AndName("uz", "Uzbek"),
        "ven" to Length2AndName("ve", "Venda"),
        "vie" to Length2AndName("vi", "Vietnamese"),
        "vol" to Length2AndName("vo", "Volapük"),
        "wln" to Length2AndName("wa", "Walloon"),
        "wol" to Length2AndName("wo", "Wolof"),
        "xho" to Length2AndName("xh", "Xhosa"),
        "yid" to Length2AndName("yi", "Yiddish"),
        "zha" to Length2AndName("za", "Chuang, Zhuang"),
        "chi" to Length2AndName("zh", "Chinese"),
        "cmn" to Length2AndName("zh", "Mandarin Chinese"), // cmn is Mandarin, which I presume translates to zh.  However, we've only encountered it in one of the Biblica texts, and there I've been asked to replace cmn by chi.
        "zom" to Length2AndName("zo", "Zokam"), // Not in ISO tables; took this from Ethnologue.
        "zul" to Length2AndName("zu", "Zulu"),
        )

    init
    {
        m_3To2.forEach { m_2To3[it.value.twoCharacterCode] = it.key }
    }
}