package org.stepbible.textconverter.nonapplicationspecificutils.bibledetails

/******************************************************************************/
/**
* Library for Bible-related information.
*
* - **[BibleAnatomy]** tells you things about Bibles in general -- whether a
*   given book is in the OT, which are single-chapter books, which verses Bibles
*   commonly omit (and therefore do not need to be reported(), etc.  The
*   information here is knowable without reference to any particular Bible, and
*   therefore is available prior to any processing.
*
* - **[BibleBookNames]** does just what the name suggests -- it supplies the
*   names used for the various book (abbreviated, short and long, as available).
*   There are two derived classes, one for USX (UBS) and one for OSIS.  These
*   make it possible to convert between book names (long, short or abbreviated)
*   and book numbers.  There is also a third derived class --
*   [BibleBookNamesTextAsSupplied] -- which contains the details relevant to
*   the particular vernacular used by the text.
*
* - **[BibleStructure]** differs from BibleAnatomy in that it contains
*   information tied to a particular text -- the number of chapters in the
*   various books, the number of verses in the various chapters, etc.  In
*   general you won't access this directly -- each variant of TextStructure
*   has its own associated BibleStructure instance, and you can access it via
*   the TextStructure instance.
*
*
*  The book numbering scheme used internally, incidentally, is that used by USX
*  (UBS).  This is organised such that the OT and NT books come out by default
*  in the standard order observed in Protestant Bibles, and also defines a
*  standard ordering for DC books.  Note that there is some difference of
*  opinion between USX and OSIS (and presumably other texts as well) as to which
*  books should be supported in the DC.
*
* @author ARA 'Jamie' Jamieson
*/

interface AAA_Doc