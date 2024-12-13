///******************************************************************************/
//package org.stepbible.textconverter.nonapplicationspecificutils.bibledetails
//
//import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
//
//
//
///******************************************************************************/
///**
// * Definitive list of book names and numbers, and the wherewithal to map
// * between them.
// *
// * @author ARA "Jamie" Jamieson
//*/
//
//object BibleBookNamesOsis: BibleBookNames(), ObjectInterface
//{
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Public                                  **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /**
//  * Gets the abbreviated name for a given book number.
//  *
//  * @param bookNo
//  * @return Abbreviated name.
//  */
//
//  operator fun get (bookNo: Int): String = numberToAbbreviatedName(bookNo)
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Private                                 **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  init { doInit() }
//
//  /****************************************************************************/
//  /* Most of this is fairly straightforward.  Be aware, though, that I may have
//     got confused while dealing with the later DC books.  I've included these
//     for completeness because they are mentioned in the USX reference manual.
//     However, in general they aren't mentioned in the Crosswire header files,
//     so although it would be legitimate for someone to give us a text
//     containing these books, we won't be able to process them.
//
//     The one possible exception is Ps2.  Here the header files do actually
//     support it, but OSIS doesn't, and since I have to create OSIS as well as
//     modules, I've opted to pretend we can't cope with it.
//
//     One other thing to bear in mind: there is a definite issue with Esg.
//     Greek Esther comes in two forms -- as a complete book, and as
//     additions which would be added to Hebrew Esther.
//
//     The name Esg here seems to imply the full book, and that's fine as far as
//     the header files are concerned (where the associated information makes it
//     clear that they do indeed have the full book in mind).
//
//     Unfortunately OSIS supports only AddEsth, which is presumably intended to
//     cover the additions.
//
//     From the point of view of the conversion processing none of this actually
//     matters -- if I'm given the additions in circumstances where I'm expecting
//     the full book, I simply create enough empty odds and ends within the
//     additions to make things work.  But there is a slight problem with the
//     OSIS, where I have to store the data under the name AddEsth even though
//     we may not be dealing with additions, but may have the full text.
//
//     And there's a further complication, in that I've seen at least one OSIS
//     file where the name EsthGr was used, despite the fact that apparently
//     OSIS does not support it.
//
//     Oh yes, and one final point.  I am not presently clear whether S3Y and
//     PrAzar are simply alternative names for the same thing.  USX supports
//     only S3Y.  OSIS supports both SgThree and PrAzar.  The header files
//     support only PrAzar.  I'm not presently too clear what we're going to do
//     about that. */
//
//  @Synchronized private fun doInit ()
//  {
//    m_BookCollectionName = "OSIS"
//    addBookDescriptor(  0, "", "", "") // Not assigned in the UBS numbering scheme.
//    addBookDescriptor(  1, "Gen", "Gen", "Genesis")
//    addBookDescriptor(  2, "Exod", "Exod", "Exodus")
//    addBookDescriptor(  3, "Lev", "Lev", "Leviticus")
//    addBookDescriptor(  4, "Num", "Num", "Numbers")
//    addBookDescriptor(  5, "Deut", "Deut", "Deuteronomy")
//    addBookDescriptor(  6, "Josh", "Josh", "Joshua")
//    addBookDescriptor(  7, "Judg", "Judg", "Judges")
//    addBookDescriptor(  8, "Ruth", "Ruth", "Ruth")
//    addBookDescriptor(  9, "1Sam", "1Sam", "ISamuel")
//    addBookDescriptor( 10, "2Sam", "2Sam", "IISamuel")
//    addBookDescriptor( 11, "1Kgs", "1Kgs", "IKings")
//    addBookDescriptor( 12, "2Kgs", "2Kgs", "IIKings")
//    addBookDescriptor( 13, "1Chr", "1Chr", "IChronicles")
//    addBookDescriptor( 14, "2Chr", "2Chr", "IIChronicles")
//    addBookDescriptor( 15, "Ezra", "Ezra", "Ezra")
//    addBookDescriptor( 16, "Neh", "Neh", "Nehemiah")
//    addBookDescriptor( 17, "Esth", "Esth", "Esther")
//    addBookDescriptor( 18, "Job", "Job", "Job")
//    addBookDescriptor( 19, "Ps", "Ps", "Psalms")
//    addBookDescriptor( 20, "Prov", "Prov", "Proverbs")
//    addBookDescriptor( 21, "Eccl", "Eccl", "Ecclesiastes")
//    addBookDescriptor( 22, "Song", "Song", "Song of Solomon")
//    addBookDescriptor( 23, "Isa", "Isa", "Isaiah")
//    addBookDescriptor( 24, "Jer", "Jer", "Jeremiah")
//    addBookDescriptor( 25, "Lam", "Lam", "Lamentations")
//    addBookDescriptor( 26, "Ezek", "Ezek", "Ezekiel")
//    addBookDescriptor( 27, "Dan", "Dan", "Daniel")
//    addBookDescriptor( 28, "Hos", "Hos", "Hosea")
//    addBookDescriptor( 29, "Joel", "Joel", "Joel")
//    addBookDescriptor( 30, "Amos", "Amos", "Amos")
//    addBookDescriptor( 31, "Obad", "Obad", "Obadiah")
//    addBookDescriptor( 32, "Jonah", "Jonah", "Jonah")
//    addBookDescriptor( 33, "Mic", "Mic", "Micah")
//    addBookDescriptor( 34, "Nah", "Nah", "Nahum")
//    addBookDescriptor( 35, "Hab", "Hab", "Habakkuk")
//    addBookDescriptor( 36, "Zeph", "Zeph", "Zephaniah")
//    addBookDescriptor( 37, "Hag", "Hag", "Haggai")
//    addBookDescriptor( 38, "Zech", "Zech", "Zechariah")
//    addBookDescriptor( 39, "Mal", "Mal", "Malachi")
//    addBookDescriptor( 40, "", "", "") // Not assigned in the UBS numbering scheme.
//    addBookDescriptor( 41, "Matt", "Matt", "Matthew")
//    addBookDescriptor( 42, "Mark", "Mark", "Mark")
//    addBookDescriptor( 43, "Luke", "Luke", "Luke")
//    addBookDescriptor( 44, "John", "John", "John")
//    addBookDescriptor( 45, "Acts", "Acts", "Acts")
//    addBookDescriptor( 46, "Rom", "Rom", "Romans")
//    addBookDescriptor( 47, "1Cor", "1Cor", "ICorinthians")
//    addBookDescriptor( 48, "2Cor", "2Cor", "IICorinthians")
//    addBookDescriptor( 49, "Gal", "Gal", "Galatians")
//    addBookDescriptor( 50, "Eph", "Eph", "Ephesians")
//    addBookDescriptor( 51, "Phil", "Phil", "Philippians")
//    addBookDescriptor( 52, "Col", "Col", "Colossians")
//    addBookDescriptor( 53, "1Thess", "1Thess", "IThessalonians")
//    addBookDescriptor( 54, "2Thess", "2Thess", "IIThessalonians")
//    addBookDescriptor( 55, "1Tim", "1Tim", "ITimothy")
//    addBookDescriptor( 56, "2Tim", "2Tim", "IITimothy")
//    addBookDescriptor( 57, "Titus", "Titus", "Titus")
//    addBookDescriptor( 58, "Phlm", "Phlm", "Philemon")
//    addBookDescriptor( 59, "Heb", "Heb", "Hebrews")
//    addBookDescriptor( 60, "Jas", "Jas", "James")
//    addBookDescriptor( 61, "1Pet", "1Pet", "IPeter")
//    addBookDescriptor( 62, "2Pet", "2Pet", "IIPeter")
//    addBookDescriptor( 63, "1John", "1John", "IJohn")
//    addBookDescriptor( 64, "2John", "2John", "IIJohn")
//    addBookDescriptor( 65, "3John", "3John", "IIIJohn")
//    addBookDescriptor( 66, "Jude", "Jude", "Jude")
//    addBookDescriptor( 67, "Rev", "Rev", "Revelation of John")
//    addBookDescriptor( 68, "Tob", "Tob", "Tobit")
//    addBookDescriptor( 69, "Jdt", "Jdt", "Judith")
//    addBookDescriptor( 70, "AddEsth", "AddEsth", "Additions to Esther")
//    addBookDescriptor( 71, "Wis", "Wis", "Wisdom")
//    addBookDescriptor( 72, "Sir", "Sir", "Sirach")
//    addBookDescriptor( 73, "Bar", "Bar", "Baruch")
//    addBookDescriptor( 74, "", C_NotSupported, "")
//    addBookDescriptor( 75, "PrAzar", "PrAzar", "Prayer of Azariah")
//    addBookDescriptor( 76, "Sus", "Sus", "Susanna")
//    addBookDescriptor( 77, "Bel", "Bel", "Bel and the Dragon")
//    addBookDescriptor( 78, "1Macc", "1Macc", "IMaccabees")
//    addBookDescriptor( 79, "2Macc", "2Macc", "IIMaccabees")
//    addBookDescriptor( 80, "3Macc", "3Macc", "IIIMaccabees")
//    addBookDescriptor( 81, "4Macc", "4Macc", "IVMaccabees")
//    addBookDescriptor( 82, "1Esd", "1Esd", "IEsdras")
//    addBookDescriptor( 83, "2Esd", "2Esd", "IIEsdras")
//    addBookDescriptor( 84, "PrMan", "PrMan", "Prayer of Manasses")
//    addBookDescriptor( 85, "AddDan", C_NotSupported, "Additions to Daniel") // UBS BookNo does not apply from here on, but I have to give a meaningful value for use when indexing tables.
//    addBookDescriptor( 86, "SgThree", C_NotSupported, "")
//    addBookDescriptor( 87, "EpJer", C_NotSupported, "Epistle of Jeremiah")
//  }
//}
