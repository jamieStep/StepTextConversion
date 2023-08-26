package org.stepbible.textconverter.support.ref

/******************************************************************************/
/**
 * For something which seems inherently quite simple, references are
 * incredibly complicated.
 *
 * To begin with, there are three flavours of reference, represented by four
 * classes, each of which derives -- directly or indirectly -- from RefBase.
 *
 * [Ref] represents an individual reference.  [RefRange] represents a range of
 * references -- eg Jn 3:16-18.  [RefCollectionPart] is either a Ref or
 * a RefRange.  And [RefCollection] represents a collection of
 * RefCollectionParts.
 *
 * Internally, individual references are represented as an array of four Ints,
 * in the order (from zero) Book, Chapter, Verse, Subverse (from here on
 * abbreviated to B, C, V and S).  Within the processing I quite frequently
 * use a single Long (aka RefKey) which combines these, such that the decimal
 * representation looks like bbbcccvvvsss (ie each element occupies three
 * digits).  The fact that this caters for 999 books, each of 999 chapters,
 * having 999 verses containing 999 subverses is not a nod in the direction
 * of a future possible expansion of the Bible -- it is simply that it makes
 * for something relatively human-readable, and has the added advantage that
 * sorting references is the same as sorting this representation.
 *
 * A value of zero for any of these elements indicates that it is absent
 * (having said which, there is a slight complication when it comes to
 * reversification, where a value of zero for the subverse appear at first
 * sight to have a specific meaning; in fact I believe it does not need to
 * so so, but we'll worry about that elsewhere).
 *
 * So much for the *internal* representation.  The *external* representation
 * is, of course, a string, and this is where the real complication comes
 * in, because we have to be able to be able to cope with a number of
 * different formats -- USX, OSIS and vernacular (and actually also yet
 * another representation which, unfortunately, is used by the
 * reversification data, but I succeed in hiding that from the rest of the
 * processing by turning it into USX format before attempting to do anything
 * with it).  And of course we need to be able to parse references which
 * already appear in the data, and write out references, for example in
 * footnotes.
 *
 * None of this external complication is apparent within Ref, RefRange
 * RefCollectionEntry or RefCollection, however -- no matter how a reference
 * is represented in the USX we are working with, or how we might want to write
 * it out, the internal representation is the same.  Or to put it another way,
 * there is no such thing as a *USX* Ref or an *OSIS* Ref or a *vernacular*
 * Ref (or RefRange or RefCollectionPart or RefCollection) -- they are all the
 * same.  The differences between USX, OSIS and vernacular become apparent
 * only when we are reading or writing textual representations.
 *
 * @author ARA 'Jamie' Jamieson
 *
 */

private class _READ_ME_