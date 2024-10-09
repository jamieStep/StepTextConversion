/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.bibledetails

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface


/******************************************************************************/
/**
 * Definitive list of book names and numbers, and the wherewithal to map
 * between them.
 * 
 * @author ARA "Jamie" Jamieson
*/

object BibleBookNamesUsx: BibleBookNames(), ObjectInterface
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
  * Gets the abbreviated name for a given book number.
  *
  * @param bookNo
  * @return Abbreviated name.
  */

  operator fun get (bookNo: Int): String = numberToAbbreviatedName(bookNo)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  init { doInit() }

  /****************************************************************************/
  /* Most of this is fairly straightforward.  Be aware, though, that I may have
     got confused while dealing with the later DC books.  I've included these
     for completeness because they are mentioned in the USX reference manual.
     However, in general they aren't mentioned in the Crosswire header files,
     so although it would be legitimate for someone to give us a text
     containing these books, we won't be able to process them.

     The one possible exception is Ps2.  Here the header files do actually
     support it, but OSIS doesn't, and since I have to create OSIS as well as
     modules, I've opted to pretend we can't cope with it.

     One other thing to bear in mind: there is a definite issue with Esg.
     Greek Esther comes in two forms -- as a complete book, and as
     additions which would be added to Hebrew Esther.

     The name Esg here seems to imply the full book, and that's fine as far as
     the header files are concerned (where the associated information makes it
     clear that they do indeed have the full book in mind).

     Unfortunately OSIS supports only AddEsth, which is presumably intended to
     cover the additions.

     From the point of view of the conversion processing none of this actually
     matters -- if I'm given the additions in circumstances where I'm expecting
     the full book, I simply create enough empty odds and ends within the
     additions to make things work.  But there is a slight problem with the
     OSIS, where I have to store the data under the name AddEsth even though
     we may not be dealing with additions, but may have the full text.

     And there's a further complication, in that I've seen at least one OSIS
     file where the name EsthGr was used, despite the fact that apparently
     OSIS does not support it.

     Oh yes, and one final point.  I am not presently clear whether S3Y and
     PrAzar are simply alternative names for the same thing.  USX supports
     only S3Y.  OSIS supports both SgThree and PrAzar.  The header files
     support only PrAzar.  I'm not presently too clear what we're going to do
     about that. */

  @Synchronized private fun doInit ()
  {
    m_BookCollectionName = "USX"
    addBookDescriptor(  0, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(  1, "Gen", "Genesis", "Genesis")
    addBookDescriptor(  2, "Exo", "Exodus", "Exodus")
    addBookDescriptor(  3, "Lev", "Leviticus", "Leviticus")
    addBookDescriptor(  4, "Num", "Numbers", "Numbers")
    addBookDescriptor(  5, "Deu", "Deuteronomy", "Deuteronomy")
    addBookDescriptor(  6, "Jos", "Joshua", "Joshua")
    addBookDescriptor(  7, "Jdg", "Judges", "Judges")
    addBookDescriptor(  8, "Rut", "Ruth", "Ruth")
    addBookDescriptor(  9, "1Sa", "1 Samuel", "1 Samuel")
    addBookDescriptor( 10, "2Sa", "2 Samuel", "2 Samuel")
    addBookDescriptor( 11, "1Ki", "1 Kings", "1 Kings")
    addBookDescriptor( 12, "2Ki", "2 Kings", "2 Kings")
    addBookDescriptor( 13, "1Ch", "1 Chronicles", "1 Chronicles")
    addBookDescriptor( 14, "2Ch", "2 Chronicles", "2 Chronicles")
    addBookDescriptor( 15, "Ezr", "Ezra", "Ezra")
    addBookDescriptor( 16, "Neh", "Nehemiah", "Nehemiah")
    addBookDescriptor( 17, "Est", "Esther", "Esther")
    addBookDescriptor( 18, "Job", "Job", "Job")
    addBookDescriptor( 19, "Psa", "Psalms", "Psalms")
    addBookDescriptor( 20, "Pro", "Proverbs", "Proverbs")
    addBookDescriptor( 21, "Ecc", "Ecclesiastes", "Ecclesiastes")
    addBookDescriptor( 22, "Sng", "Song of Solomon", "Song of Solomon")
    addBookDescriptor( 23, "Isa", "Isaiah", "Isaiah")
    addBookDescriptor( 24, "Jer", "Jeremiah", "Jeremiah")
    addBookDescriptor( 25, "Lam", "Lamentations", "Lamentations")
    addBookDescriptor( 26, "Ezk", "Ezekiel", "Ezekiel")
    addBookDescriptor( 27, "Dan", "Daniel", "Daniel")
    addBookDescriptor( 28, "Hos", "Hosea", "Hosea")
    addBookDescriptor( 29, "Jol", "Joel", "Joel")
    addBookDescriptor( 30, "Amo", "Amos", "Amos")
    addBookDescriptor( 31, "Oba", "Obadiah", "Obadiah")
    addBookDescriptor( 32, "Jon", "Jonah", "Jonah")
    addBookDescriptor( 33, "Mic", "Micah", "Micah")
    addBookDescriptor( 34, "Nam", "Nahum", "Nahum")
    addBookDescriptor( 35, "Hab", "Habakkuk", "Habakkuk")
    addBookDescriptor( 36, "Zep", "Zephaniah", "Zephaniah")
    addBookDescriptor( 37, "Hag", "Haggai", "Haggai")
    addBookDescriptor( 38, "Zec", "Zechariah", "Zechariah")
    addBookDescriptor( 39, "Mal", "Malachi", "Malachi")
    addBookDescriptor( 40, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 41, "Mat", "Matthew", "Matthew")
    addBookDescriptor( 42, "Mrk", "Mark", "Mark")
    addBookDescriptor( 43, "Luk", "Luke", "Luke")
    addBookDescriptor( 44, "Jhn", "John", "John")
    addBookDescriptor( 45, "Act", "Acts", "Acts")
    addBookDescriptor( 46, "Rom", "Romans", "Romans")
    addBookDescriptor( 47, "1Co", "1 Corinthians", "1 Corinthians")
    addBookDescriptor( 48, "2Co", "2 Corinthians", "2 Corinthians")
    addBookDescriptor( 49, "Gal", "Galatians", "Galatians")
    addBookDescriptor( 50, "Eph", "Ephesians", "Ephesians")
    addBookDescriptor( 51, "Php", "Philippians", "Philippians")
    addBookDescriptor( 52, "Col", "Colossians", "Colossians")
    addBookDescriptor( 53, "1Th", "1 Thessalonians", "1 Thessalonians")
    addBookDescriptor( 54, "2Th", "2 Thessalonians", "2 Thessalonians")
    addBookDescriptor( 55, "1Ti", "1 Timothy", "1 Timothy")
    addBookDescriptor( 56, "2Ti", "2 Timothy", "2 Timothy")
    addBookDescriptor( 57, "Tit", "Titus", "Titus")
    addBookDescriptor( 58, "Phm", "Philemon", "Philemon")
    addBookDescriptor( 59, "Heb", "Hebrews", "Hebrews")
    addBookDescriptor( 60, "Jas", "James", "James")
    addBookDescriptor( 61, "1Pe", "1 Peter", "1 Peter")
    addBookDescriptor( 62, "2Pe", "2 Peter", "2 Peter")
    addBookDescriptor( 63, "1Jn", "1 John", "1 John")
    addBookDescriptor( 64, "2Jn", "2 John", "2 John")
    addBookDescriptor( 65, "3Jn", "3 John", "3 John")
    addBookDescriptor( 66, "Jud", "Jude", "Jude")
    addBookDescriptor( 67, "Rev", "Revelation", "Revelation")
    addBookDescriptor( 68, "Tob", "Tobit", "Tobit")
    addBookDescriptor( 69, "Jdt", "Judith", "Judith")
    addBookDescriptor( 70, "Esg", "Additions to Esther", "Additions to Esther (Greek)")
    addBookDescriptor( 71, "Wis", "Wisdom of Solomon", "Wisdom of Solomon")
    addBookDescriptor( 72, "Sir", "Sirach", "Sirach")
    addBookDescriptor( 73, "Bar", "Baruch", "Baruch")
    addBookDescriptor( 74, "Lje", C_NotSupported, "Epistle of Jeremiah")
    addBookDescriptor( 75, "S3y", "Song of the Three Young Men", "Song of the Three Young Men")
    addBookDescriptor( 76, "Sus", "Susannah", "Susannah")
    addBookDescriptor( 77, "Bel", "Bel and the Dragon", "Bel and the Dragon")
    addBookDescriptor( 78, "1Ma", "1 Maccabees", "1 Maccabees")
    addBookDescriptor( 79, "2Ma", "2 Maccabees", "2 Maccabees")
    addBookDescriptor( 80, "3Ma", "3 Maccabees", "3 Maccabees")
    addBookDescriptor( 81, "4Ma", "4 Maccabees", "4 Maccabees")
    addBookDescriptor( 82, "1Es", "1 Esdras (Greek)", "1 Esdras (Greek)")
    addBookDescriptor( 83, "2Es", "2 Esdras (Latin)", "2 Esdras (Latin)")
    addBookDescriptor( 84, "Man", "Prayer of Manasseh", "Prayer of Manasseh")
    addBookDescriptor( 85, "Ps2", C_NotSupported, "Psalm 151")
    addBookDescriptor( 86, "Oda", C_NotSupported, "Odae")
    addBookDescriptor( 87, "Pss", C_NotSupported, "Psalms of Solomon") // Supported by UBS, but not by NRSVA.
    addBookDescriptor( 88, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 89, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 90, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 91, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 92, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 93, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 94, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 95, "4Es", C_NotSupported, "") // Not assigned in the UBS numbering scheme, but I need to have 4Es so that the reversification data works.
    addBookDescriptor( 96, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 97, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 98, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 99, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(100, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(101, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(102, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(103, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(104, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(105, "Eza", C_NotSupported, "4 Ezra")
    addBookDescriptor(106, "5Ez", C_NotSupported, "5 Ezra")
    addBookDescriptor(107, "6Ez", C_NotSupported, "6 Ezra")
    addBookDescriptor(108, "Dag", C_NotSupported, "Greek Daniel")
    addBookDescriptor(109, "Ps3", C_NotSupported, "Psalms 152-155")
    addBookDescriptor(110, "2Ba", C_NotSupported, "3 Baruch (Apocalypse)")
    addBookDescriptor(111, "Lba", C_NotSupported, "Letter of Baruch")
    addBookDescriptor(112, "Jub", C_NotSupported, "Jubilees")
  }
}
