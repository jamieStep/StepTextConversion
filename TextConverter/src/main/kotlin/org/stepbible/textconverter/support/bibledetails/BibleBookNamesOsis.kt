/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails



/******************************************************************************/
/**
 * Definitive list of book names and numbers, and the wherewithal to map
 * between them.
 * 
 * @author ARA "Jamie" Jamieson
*/

object BibleBookNamesOsis: BibleBookNames()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  init
  {
    m_BookCollectionName = "OSIS"
    addBookDescriptor(  0, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(  1, "Gen", "Gen", "Genesis")
    addBookDescriptor(  2, "Exod", "Exod", "Exodus")
    addBookDescriptor(  3, "Lev", "Lev", "Leviticus")
    addBookDescriptor(  4, "Num", "Num", "Numbers")
    addBookDescriptor(  5, "Deut", "Deut", "Deuteronomy")
    addBookDescriptor(  6, "Josh", "Josh", "Joshua")
    addBookDescriptor(  7, "Judg", "Judg", "Judges")
    addBookDescriptor(  8, "Ruth", "Ruth", "Ruth")
    addBookDescriptor(  9, "1Sam", "1Sam", "I Samuel")
    addBookDescriptor( 10, "2Sam", "2Sam", "II Samuel")
    addBookDescriptor( 11, "1Kgs", "1Kgs", "I Kings")
    addBookDescriptor( 12, "2Kgs", "2Kgs", "II Kings")
    addBookDescriptor( 13, "1Chr", "1Chr", "I Chronicles")
    addBookDescriptor( 14, "2Chr", "2Chr", "II Chronicles")
    addBookDescriptor( 15, "Ezra", "Ezra", "Ezra")
    addBookDescriptor( 16, "Neh", "Neh", "Nehemiah")
    addBookDescriptor( 17, "Esth", "Esth", "Esther")
    addBookDescriptor( 18, "Job", "Job", "Job")
    addBookDescriptor( 19, "Ps", "Ps", "Psalms")
    addBookDescriptor( 20, "Prov", "Prov", "Proverbs")
    addBookDescriptor( 21, "Eccl", "Eccl", "Ecclesiastes")
    addBookDescriptor( 22, "Song", "Song", "Song of Solomon")
    addBookDescriptor( 23, "Isa", "Isa", "Isaiah")
    addBookDescriptor( 24, "Jer", "Jer", "Jeremiah")
    addBookDescriptor( 25, "Lam", "Lam", "Lamentations")
    addBookDescriptor( 26, "Ezek", "Ezek", "Ezekiel")
    addBookDescriptor( 27, "Dan", "Dan", "Daniel")
    addBookDescriptor( 28, "Hos", "Hos", "Hosea")
    addBookDescriptor( 29, "Joel", "Joel", "Joel")
    addBookDescriptor( 30, "Amos", "Amos", "Amos")
    addBookDescriptor( 31, "Obad", "Obad", "Obadiah")
    addBookDescriptor( 32, "Jonah", "Jonah", "Jonah")
    addBookDescriptor( 33, "Mic", "Mic", "Micah")
    addBookDescriptor( 34, "Nah", "Nah", "Nahum")
    addBookDescriptor( 35, "Hab", "Hab", "Habakkuk")
    addBookDescriptor( 36, "Zeph", "Zeph", "Zephaniah")
    addBookDescriptor( 37, "Hag", "Hag", "Haggai")
    addBookDescriptor( 38, "Zech", "Zech", "Zechariah")
    addBookDescriptor( 39, "Mal", "Mal", "Malachi")
    addBookDescriptor( 40, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 41, "Matt", "Matt", "Matthew")
    addBookDescriptor( 42, "Mark", "Mark", "Mark")
    addBookDescriptor( 43, "Luke", "Luke", "Luke")
    addBookDescriptor( 44, "John", "John", "John")
    addBookDescriptor( 45, "Acts", "Acts", "Acts")
    addBookDescriptor( 46, "Rom", "Rom", "Romans")
    addBookDescriptor( 47, "1Cor", "1Cor", "I Corinthians")
    addBookDescriptor( 48, "2Cor", "2Cor", "II Corinthians")
    addBookDescriptor( 49, "Gal", "Gal", "Galatians")
    addBookDescriptor( 50, "Eph", "Eph", "Ephesians")
    addBookDescriptor( 51, "Phil", "Phil", "Philippians")
    addBookDescriptor( 52, "Col", "Col", "Colossians")
    addBookDescriptor( 53, "1Thess", "1Thess", "I Thessalonians")
    addBookDescriptor( 54, "2Thess", "2Thess", "II Thessalonians")
    addBookDescriptor( 55, "1Tim", "1Tim", "I Timothy")
    addBookDescriptor( 56, "2Tim", "2Tim", "II Timothy")
    addBookDescriptor( 57, "Titus", "Titus", "Titus")
    addBookDescriptor( 58, "Phlm", "Phlm", "Philemon")
    addBookDescriptor( 59, "Heb", "Heb", "Hebrews")
    addBookDescriptor( 60, "Jas", "Jas", "James")
    addBookDescriptor( 61, "1Pet", "1Pet", "I Peter")
    addBookDescriptor( 62, "2Pet", "2Pet", "II Peter")
    addBookDescriptor( 63, "1John", "1John", "I John")
    addBookDescriptor( 64, "2John", "2John", "II John")
    addBookDescriptor( 65, "3John", "3John", "III John")
    addBookDescriptor( 66, "Jude", "Jude", "Jude")
    addBookDescriptor( 67, "Rev", "Rev", "Revelation of John")
    addBookDescriptor( 68, "Tob", "Tob", "Tobit")
    addBookDescriptor( 69, "Jdt", "Jdt", "Judith")
    addBookDescriptor( 70, "EsthGr", "EsthGr", "Additions to Esther")
    addBookDescriptor( 71, "Wis", "Wis", "Wisdom")
    addBookDescriptor( 72, "Sir", "Sir", "Sirach")
    addBookDescriptor( 73, "Bar", "Bar", "Baruch")
    addBookDescriptor( 74, "EpJer", "EpJer", "Epistle of Jeremiah")
    addBookDescriptor( 75, "PrAzar", "PrAzar", "Prayer of Azariah")
    addBookDescriptor( 76, "Sus", "Sus", "Susanna")
    addBookDescriptor( 77, "Bel", "Bel", "Bel and the Dragon")
    addBookDescriptor( 78, "1Macc", "1Macc", "I Maccabees")
    addBookDescriptor( 79, "2Macc", "2Macc", "II Maccabees")
    addBookDescriptor( 80, "3Macc", "3Macc", "III Maccabees")
    addBookDescriptor( 81, "4Macc", "4Macc", "IV Maccabees")
    addBookDescriptor( 82, "1Esd", "1Esd", "I Esdras")
    addBookDescriptor( 83, "2Esd", "2Esd", "II Esdras")
    addBookDescriptor( 84, "PrMan", "PrMan", "Prayer of Manasses")
    addBookDescriptor( 85, "AddPs", "AddPs", "Additional Psalm")
    addBookDescriptor( 86, "Odes", "Odes", "Odes")
    addBookDescriptor( 87, "PssSol", "PssSol", "PsalmsOfSolomon") // Supported by UBS, but not by NRSVA.
    addBookDescriptor( 88, "JoshA", "Joshua A", "Joshua A")
    addBookDescriptor( 89, "JudgB", "Judges B", "Judges B")
    addBookDescriptor( 90, "TobS", "Tobit S", "Tobit S")
    addBookDescriptor( 91, "SusTh", "Susannah θ", "Susannah θ")
    addBookDescriptor( 92, "DanTh", "Daniel θ", "Daniel θ")
    addBookDescriptor( 93, "BelTh", "Bel and the Dragon θ", "Bel and the Dragon θ")
    addBookDescriptor( 94, "EpLao", "Epistle to the Laodiceans", "Epistle to the Laodiceans")
    addBookDescriptor( 95, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 96, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 97, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 98, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor( 99, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(100, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(101, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(102, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(103, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(104, "", "", "") // Not assigned in the UBS numbering scheme.
    addBookDescriptor(105, "4Ezra", "4 Ezra", "4 Ezra")
    addBookDescriptor(106, "5Ezra", "5 Ezra", "5 Ezra")
    addBookDescriptor(107, "6Ezra", "6 Ezra", "6 Ezra")
    addBookDescriptor(108, "DanGr", "DanGr", "Greek Daniel") // Supported by UBS, but not by NRSVA.
  }
}
