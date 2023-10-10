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
        "afr" to Length2AndName("af", "Afrikaans"),
        "aka" to Length2AndName("ak", "Akan"),
        "alb" to Length2AndName("sq", "Albanian"),
        "amh" to Length2AndName("am", "Amharic"),
        "ara" to Length2AndName("ar", "Arabic"),
        "arb" to Length2AndName("ar", "Standard Arabic"),
        "arg" to Length2AndName("an", "Aragonese"),
        "arm" to Length2AndName("hy", "Armenian"),
        "asm" to Length2AndName("as", "Assamese"),
        "ava" to Length2AndName("av", "Avaric"),
        "ave" to Length2AndName("ae", "Avestan"),
        "aym" to Length2AndName("ay", "Aymara"),
        "aze" to Length2AndName("az", "Azerbaijani"),
        "bak" to Length2AndName("ba", "Bashkir"),
        "bam" to Length2AndName("bm", "Bambara"),
        "baq" to Length2AndName("eu", "Basque"),
        "bel" to Length2AndName("be", "Belarusian"),
        "ben" to Length2AndName("bn", "Bengali"),
        "bih" to Length2AndName("bh", "Bihari languages"),
        "bis" to Length2AndName("bi", "Bislama"),
        "bos" to Length2AndName("bs", "Bosnian"),
        "bre" to Length2AndName("br", "Breton"),
        "bul" to Length2AndName("bg", "Bulgarian"),
        "bur" to Length2AndName("my", "Burmese"),
        "cat" to Length2AndName("ca", "Catalan, Valencian"),
        "ceb" to Length2AndName("ceb", "Cebuano"),
        "ces" to Length2AndName("cs", "Czech"),
        "cha" to Length2AndName("ch", "Chamorro"),
        "che" to Length2AndName("ce", "Chechen"), // No 2-char code.
        "chi" to Length2AndName("zh", "Chinese"),
        "chu" to Length2AndName("cu", "Church Slavic, Church Slavonic, Old Bulgarian, Old Church Slavonic, Old Slavonic"),
        "chv" to Length2AndName("cv", "Chuvash"),
        "ckb" to Length2AndName("ckb", "Central Kurdish"), // No 2-char code.
        "cmn" to Length2AndName("zh", "Mandarin Chinese"), // cmn is Mandarin, which I presume translates to zh.  However, we've only encountered it in one of the Biblica texts, and there I've been asked to replace cmn by chi.
        "cor" to Length2AndName("kw", "Cornish"),
        "cos" to Length2AndName("co", "Corsican"),
        "cre" to Length2AndName("cr", "Cree"),
        "dan" to Length2AndName("da", "Danish"),
        "deu" to Length2AndName("de", "German"),
        "div" to Length2AndName("dv", "Dhivehi, Divehi, Maldivian"),
        "dzo" to Length2AndName("dz", "Dzongkha"),
        "eng" to Length2AndName("en", "English"),
        "epo" to Length2AndName("eo", "Esperanto"),
        "est" to Length2AndName("et", "Estonian"),
        "ewe" to Length2AndName("ee", "Ewe"),
        "fao" to Length2AndName("fo", "Faroese"),
        "fij" to Length2AndName("fj", "Fijian"),
        "fin" to Length2AndName("fi", "Finnish"),
        "fra" to Length2AndName("fr", "French"),
        "fry" to Length2AndName("fy", "Western Frisian"),
        "ful" to Length2AndName("ff", "Fulah"),
        "gbr" to Length2AndName("gbr", "Gbagyi"), // No 2-char code.
        "geo" to Length2AndName("ka", "Georgian"),
        "gla" to Length2AndName("gd", "Gaelic, Scottish Gaelic"),
        "gle" to Length2AndName("ga", "Irish"),
        "glg" to Length2AndName("gl", "Galician"),
        "glv" to Length2AndName("gv", "Manx"),
        "grc" to Length2AndName("grc", "Ancient Greek to 1453"),
        "gre" to Length2AndName("el", "Modern Greek (1453-)"),
        "grn" to Length2AndName("gn", "Guarani"),
        "guj" to Length2AndName("gu", "Gujarati"),
        "hat" to Length2AndName("ht", "Haitian, Haitian Creole"),
        "hau" to Length2AndName("ha", "Hausa"),
        "hbs" to Length2AndName("sh", "Serbo-Croatian"),
        "heb" to Length2AndName("he", "Hebrew"),
        "her" to Length2AndName("hz", "Herero"),
        "hil" to Length2AndName("hil", "Hiligaynon"), // No 2-char code.
        "hin" to Length2AndName("hi", "Hindi"),
        "hmo" to Length2AndName("ho", "Hiri Motu"),
        "hne" to Length2AndName("hne", "Chhattisgarhi"), // No 2-char code.
        "hrv" to Length2AndName("hr", "Croatian"),
        "hun" to Length2AndName("hu", "Hungarian"),
        "ibo" to Length2AndName("ig", "Igbo"),
        "ice" to Length2AndName("is", "Icelandic"),
        "ido" to Length2AndName("io", "Ido"),
        "iii" to Length2AndName("ii", "Nuosu, Sichuan Yi"),
        "iku" to Length2AndName("iu", "Inuktitut"),
        "ile" to Length2AndName("ie", "Interlingue, Occidental"),
        "ina" to Length2AndName("ia", "Interlingua (International Auxiliary Language Association)"),
        "ind" to Length2AndName("id", "Indonesian"),
        "ipk" to Length2AndName("ik", "Inupiaq"),
        "ita" to Length2AndName("it", "Italian"),
        "jav" to Length2AndName("jv", "Javanese"),
        "jpn" to Length2AndName("ja", "Japanese"),
        "kal" to Length2AndName("kl", "Greenlandic, Kalaallisut"),
        "kan" to Length2AndName("kn", "Kannada"),
        "kas" to Length2AndName("ks", "Kashmiri"),
        "kau" to Length2AndName("kr", "Kanuri"),
        "kaz" to Length2AndName("kk", "Kazakh"),
        "khm" to Length2AndName("km", "Central Khmer, Khmer"),
        "kik" to Length2AndName("ki", "Gikuyu, Kikuyu"),
        "kin" to Length2AndName("rw", "Kinyarwanda"),
        "kir" to Length2AndName("ky", "Kirghiz, Kyrgyz"),
        "kom" to Length2AndName("kv", "Komi"),
        "kon" to Length2AndName("kg", "Kongo"),
        "kor" to Length2AndName("ko", "Korean"),
        "kua" to Length2AndName("kj", "Kuanyama, Kwanyama"),
        "kur" to Length2AndName("ku", "Kurdish"),
        "lao" to Length2AndName("lo", "Lao"),
        "lat" to Length2AndName("la", "Latin"),
        "lav" to Length2AndName("lv", "Latvian"),
        "lim" to Length2AndName("li", "Limburgan, Limburger, Limburgish"),
        "lin" to Length2AndName("ln", "Lingala"),
        "lit" to Length2AndName("lt", "Lithuanian"),
        "ltz" to Length2AndName("lb", "Letzeburgesch, Luxembourgish"),
        "lub" to Length2AndName("lu", "Luba-Katanga"),
        "lug" to Length2AndName("lg", "Ganda"),
        "mac" to Length2AndName("mk", "Macedonian"),
        "mah" to Length2AndName("mh", "Marshallese"),
        "mal" to Length2AndName("ml", "Malayalam"),
        "mao" to Length2AndName("mi", "Maori"),
        "mar" to Length2AndName("mr", "Marathi"),
        "may" to Length2AndName("ms", "Malay (macrolanguage)"),
        "mlg" to Length2AndName("mg", "Malagasy"),
        "mlt" to Length2AndName("mt", "Maltese"),
        "mon" to Length2AndName("mn", "Mongolian"),
        "nau" to Length2AndName("na", "Nauru"),
        "nav" to Length2AndName("nv", "Navaho, Navajo"),
        "nbl" to Length2AndName("nr", "South Ndebele"),
        "nde" to Length2AndName("nd", "North Ndebele"),
        "ndo" to Length2AndName("ng", "Ndonga"),
        "nep" to Length2AndName("ne", "Nepali (macrolanguage)"),
        "nld" to Length2AndName("nl", "Dutch, Flemish"),
        "nno" to Length2AndName("nn", "Norwegian Nynorsk"),
        "nob" to Length2AndName("nb", "Norwegian Bokmål"),
        "nor" to Length2AndName("no", "Norwegian"),
        "nya" to Length2AndName("ny", "Chewa, Chichewa, Nyanja"),
        "oci" to Length2AndName("oc", "Occitan (post 1500)"),
        "oji" to Length2AndName("oj", "Ojibwa"),
        "ori" to Length2AndName("or", "Oriya (macrolanguage)"),
        "orm" to Length2AndName("om", "Oromo"),
        "oss" to Length2AndName("os", "Ossetian, Ossetic"),
        "pan" to Length2AndName("pa", "Panjabi, Punjabi"),
        "per" to Length2AndName("fa", "Persian"),
        "pes" to Length2AndName("pes", "Persian, Iranian"), // No 2-char code.
        "pli" to Length2AndName("pi", "Pali"),
        "pol" to Length2AndName("pl", "Polish"),
        "por" to Length2AndName("pt", "Portuguese"),
        "pus" to Length2AndName("ps", "Pashto, Pushto"),
        "que" to Length2AndName("qu", "Quechua"),
        "roh" to Length2AndName("rm", "Romansh"),
        "ron" to Length2AndName("ro", "Romanian"),
        "rum" to Length2AndName("ro", "Moldavian, Moldovan, Romanian"),
        "run" to Length2AndName("rn", "Rundi"),
        "rus" to Length2AndName("ru", "Russian"),
        "sag" to Length2AndName("sg", "Sango"),
        "san" to Length2AndName("sa", "Sanskrit"),
        "sin" to Length2AndName("si", "Sinhala, Sinhalese"),
        "slo" to Length2AndName("sk", "Slovak"),
        "slv" to Length2AndName("sl", "Slovenian"),
        "sme" to Length2AndName("se", "Northern Sami"),
        "smo" to Length2AndName("sm", "Samoan"),
        "sna" to Length2AndName("sn", "Shona"),
        "snd" to Length2AndName("sd", "Sindhi"),
        "som" to Length2AndName("so", "Somali"),
        "sot" to Length2AndName("st", "Southern Sotho"),
        "spa" to Length2AndName("es", "Castilian, Spanish"),
        "srd" to Length2AndName("sc", "Sardinian"),
        "srp" to Length2AndName("sr", "Serbian"),
        "ssw" to Length2AndName("ss", "Swati"),
        "sun" to Length2AndName("su", "Sundanese"),
        "swa" to Length2AndName("sw", "Swahili (macrolanguage)"),
        "swe" to Length2AndName("sv", "Swedish"),
        "swh" to Length2AndName("swh", "Swahili"), // No 2-char code.
        "tah" to Length2AndName("ty", "Tahitian"),
        "tam" to Length2AndName("ta", "Tamil"),
        "tat" to Length2AndName("tt", "Tatar"),
        "tel" to Length2AndName("te", "Telugu"),
        "tgk" to Length2AndName("tg", "Tajik"),
        "tgl" to Length2AndName("tl", "Tagalog"),
        "tha" to Length2AndName("th", "Thai"),
        "tib" to Length2AndName("bo", "Tibetan"),
        "tir" to Length2AndName("ti", "Tigrinya"),
        "ton" to Length2AndName("to", "Tonga (Tonga Islands)"),
        "tsn" to Length2AndName("tn", "Tswana"),
        "tso" to Length2AndName("ts", "Tsonga"),
        "tuk" to Length2AndName("tk", "Turkmen"),
        "tur" to Length2AndName("tr", "Turkish"),
        "twi" to Length2AndName("tw", "Twi"),
        "uig" to Length2AndName("ug", "Uighur, Uyghur"),
        "ukr" to Length2AndName("uk", "Ukrainian"),
        "urd" to Length2AndName("ur", "Urdu"),
        "uzb" to Length2AndName("uz", "Uzbek"),
        "ven" to Length2AndName("ve", "Venda"),
        "vie" to Length2AndName("vi", "Vietnamese"),
        "vol" to Length2AndName("vo", "Volapük"),
        "wel" to Length2AndName("cy", "Welsh"),
        "wln" to Length2AndName("wa", "Walloon"),
        "wol" to Length2AndName("wo", "Wolof"),
        "xho" to Length2AndName("xh", "Xhosa"),
        "yid" to Length2AndName("yi", "Yiddish"),
        "yom" to Length2AndName("yom", "Yombe"), // No 2-char code.
        "yor" to Length2AndName("yo", "Yoruba"),
        "zha" to Length2AndName("za", "Chuang, Zhuang"),
        "zom" to Length2AndName("zo", "Zokam"), // Not in ISO tables; took this from Ethnologue.
        "zul" to Length2AndName("zu", "Zulu"),
        )

    init
    {
        m_3To2.forEach { m_2To3[it.value.twoCharacterCode] = it.key }
    }
}