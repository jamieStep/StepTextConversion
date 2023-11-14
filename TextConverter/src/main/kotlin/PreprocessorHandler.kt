package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.runCommand
import org.stepbible.textconverter.support.miscellaneous.Zip
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader

/******************************************************************************/
/**
 * Handles preprocessing.
 *
 * USX being a rather complex standard (and at the same time lacking facilities
 * which translators commonly seem to require), it quite often happens that
 * we receive texts which are incorrect in some manner (or at the very least,
 * which do not fit with our processing).
 * 
 * The manner in which such texts deviate differs from text to text.  This means
 * it is not really feasible to extend the converter to handle all of them -- to
 * do so would produce something even more baroque than we already have.  I have
 * therefore made provision for you to write your own text-specific
 * preprocessing, and to have this automatically picked up and applied by the
 * converter before it starts its work proper.
 * 
 * I support two different forms of preprocessing.  In one, the preprocessor is
 * run standalone.  In this version, the preprocessor reads the raw USX files
 * and creates new files as necessary.  In the other, the preprocessor
 * effectively becomes part of the converter for the duration of the run, and
 * works in-memory -- it is passed a document object module, and returns a
 * revised version of the module.  These are described in detail below.
 * 
 * 
 * 
 * 
 * ## Standalone preprocessing
 *
 * In standalone preprocessing, you create a runnable JAR, or any other
 * program (.jar, .js. .exe, .py, .bat etc) which is capable of taking
 * arguments from the command line.
 *
 * You inform the converter of the requirement to run the preprocessor by
 * setting the configuration parameter stepRunnablePreprocessorFilePath to give
 * the path to the program.
 *
 * Particularly with scripting languages, you may also need to give details of
 * how to run the interpreter, to which end you can use the configuration
 * parameter stepRunnablePreprocessorCommandPrefix.
 *
 * Thus, for example, if you have a Perl script C:\Users\Me\Documents\perl.pl,
 * you give stepRunnablePreprocessorFilePath as 'C:\Users\Me\Documents\perl.pl'
 * and stepRunnablePreprocessorCommandPrefix as 'perl', and the converter will
 * run this as 'perl "C:\Users\Me\Documents\perl.pl <args>'.
 *
 * There is no need to use stepRunnablePreprocessorCommandPrefix with .jar
 * files, .py files or .js files, unless you need to do something odd: the
 * converter will assume 'java -jar', 'python' or 'node.js' respectively.
 *
 * stepRunnablePreprocessorFilePath works in much the same way as $include
 * configuration statements, so you can give the path as relative to the
 * root folder of the text (using $root), relative to the metadata folder,
 * etc.
 *
 * Including arguments, a command line might look like:
 *
 *   C:\Users\Me\Documents\preprocessor.exe <outputFolderPath> <inputFolderPath> [<bookList>]
 *
 * The arguments are as follows:
 *
 * - inputFolderPath: The folder in which the raw USX files are stored.  The
 *     processing should run over all *.usx files in this folder (or selected
 *     ones -- see the discussion of bookList below), and apply modifications
 *     as necessary.  Anything other than *.usx should be ignored.
 *
 * - outputFolderPath: The folder in which to store output files (all of which
 *     must be valid USX).  There is no need to store a file in this folder
 *     unless the processing has changed it (but no harm in storing it
 *     regardless).  Files should be stored under the same name as the
 *     corresponding input file.  The output folder will be empty at the time
 *     the preprocessing is invoked, so there is no need to clear it before
 *     starting work.
 *
 * - bookList: This may or may not appear, and even if it does, there is no
 *     requirement for the preprocessor to take any notice of it.  It gives a
 *     list of UBS (USX) book abbreviations which are the ones which the
 *     preprocessor *must* handle on this run.  Any others can be ignored for
 *     the sake of speeding things up and reducing file io, but there is no
 *     problem if you choose to process them regardless.
 *
 *
 * It is unlikely that you will want to create in the output folder a file for
 * which there is no input counterpart, but you can do so if there is a need
 * for it.  In his case you can give it any name you like, but it must have
 * the .usx extension.
 *
 * The preprocess can also *prevent* a given file from being processed, by
 * creating in the output folder an empty file of the given name.  Again,
 * though, I should imagine this is an unlikely eventuality.
 *
 * The converter will pick up files from the output folder in preference to
 * corresponding files in the input folder, where both exist.
 *
 * There are several advantages to using a standalone preprocessor:
 *
 * - In general it is easy to debug, because you can indeed run it standalone.
 *
 * - The converter validates its own output against the input which it used.
 *   With standalone preprocessing, the converter only actually gets to
 *   see the preprocessed data, and this makes validation less of a challenge.
 *   (That may make more sense after you have read the discussion of callable
 *   preprocessing below.)
 *
 *
 * There are downsides, however, of which probably the main one is the
 * increased file io in creating revised copies of the input files.
 *
 *
 *
 *
 * ## Callable preprocessing
 *
 * With callable preprocessing, you supply an external JAR file which conforms
 * to certain rules, and this is incorporated into the converter temporarily at
 * runtime.
 *
 * To tell the converter it needs to use it, you use the configuration parameter
 * stepCallablePreprocessorFilePath to give the path to the JAR.  See the
 * discussion of standalone processing for details of the various syntactic
 * sweeteners you can use for this.
 *
 * The easiest way to describe what the code in this JAR should look like is to
 * give an example:
 *
 *      package org.stepbible.preprocessor  // You *must* use this package.
 *
 *      import org.w3c.dom.*                // You'll almost certainly need this.
 *
 *
 *      fun main(args: Array<String>) { }   // See discussion below.
 *
 *
 *      class Preprocessor
 *      {
 *        fun getTextForValidation (text: String): String
 *        {
 *          return text.replace("¶", "")
 *        }
 *
 *        fun preprocess (doc: Document): List<String>?
 *        {
 *          val textNodes = findAllTextNodes(doc)
 *          textNodes.filter { "¶" in it.textContent } .forEach { it.textContent = it.textContent.replace("¶", "")}
 *          return null
 *        }
 *
 *        // Anything else you need.
 *      }
 *
 *
 *
 *
 *
 * 
 * Strictly speaking 'fun main ()' above is not needed, because nothing will
 * attempt to call the main entry point.  However I had problems in Intellij IDEA
 * without it -- it seemed to be difficult to get the JAR file set up correctly,
 * and syntax highlighting in the editor wasn't working correctly.
 *
 * 'preprocess' is passed a document and updates it.  In the example here, I'm
 * stripping paragraph markers from all text nodes.  'findAllTextNodes' is part
 * of the 'Anything else you need'.  It can return error, warning and
 * information messages via its output.  Each should be prefixed by 'ERROR: ',
 * 'WARNING: ' or 'INFORMATION: '.  They can appear in any order, and the
 * different flavours can be interleaved with each other if that is easier.
 *
 * getTextForValidation is a support method for the converter's validation
 * processing.  The validation processing compares the canonical content of
 * verses before and after -- and for the 'before' version, it will be looking
 * at the raw USX.  If the preprocessor has done anything to change that (as
 * here), getTextForValidation needs to tell it what the content looked like
 * after preprocessing.  (Where it has not, in fact, changed the text, it should
 * simply return its argument.)
 *
 * The advantage of this form of preprocessing is that it fits quite neatly into
 * the overall conversion process, requires no additional io, and does not have
 * the complications inherent in standalone processing of having to locate all
 * potentially relevant files.
 *
 * The downsides are the difficulty of debugging the code, and the need for
 * getTextForValidation.  In the above example it was easy to implement, but
 * it is not hard to imagine scenarios in which it would be appreciably more
 * complicated.  In these, it might well be better to resort to the standalone
 * preprocessing, because that way the converter will automatically use the
 * preprocessor output for validation purposes.
 *
 *
 *
 *
 *
 *
 * ## Creating a callable preprocessor
 *
 * The mechanics of creating a callable preprocessor using Intellij IDEA have
 * proved somewhat challenging, so I give details of what I think is needed
 * below.  Take this with a pinch of salt; even now I'm not sure that this is
 * complete and correct, nor that there aren't things below which are
 * unnecessary.
 *
 * - Make a Kotlin project for the preprocessor.  Have IDEA create the sample
 *   Main for you -- we don't need the main function for the processing, but
 *   without it, IDEA seems to go wrong: syntax highlighting doesn't work, and
 *   when you build the JAR as an artifact, the relevant code doesn't end up in
 *   it.
 *
 * - Create the code according to the template above.
 *
 * - Go to File/ProjectStructure/Artifacts, hit the '+' at the top left and then
 *   select JAR and then FromModulesWithDependencies.
 *
 * - In the resulting window ... Against Modules, select the item with ‘.main’
 *   against it.  Set MainClass (even though we're never going to use the main
 *   method).  Make sure ExtractToTheTargetJar is selected.
 *
 * - Build the artifact.  It will turn up in the 'out' folder, from where you
 *   can copy it to the Preprocessor folder under your text root.
 *
 * - Don't forget to rebuild it and copy it again each time you need to change
 *   it.
 *
 * @author ARA Jamieson
 */


object PreprocessorHandler
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * For use where we may have callable preprocessor.  Updates a single document.
  * Note that there is no harm in calling this even where we do not have a
  * callable preprocessor: if we do not, it will simply do nothing.
  *
  * @param doc Document to be preprocessed.
  *
  * @return List of errors / warnings / informationals, or null
  */

  fun applyCallablePreprocessor (doc: Document): List<String>?
  {
    if (!m_CheckedExistence)
      initialise()

    if (null != m_MethodPreprocess)
    {
      val bookName = Dom.findNodeByName(doc, "book")!!["code"]!!
      Dbg.reportProgress("  Preprocessing $bookName")
      return m_MethodPreprocess!!.invoke(m_PreprocessorInstance!!, doc) as List<String>?
    }

    return null
  }


  /****************************************************************************/
  /**
  * Applies a standalone preprocessor where this is appropriate.  For more
  * details about standalone preprocessors, see the head-of-class comments.
  *
  * @return True if anything was done.
  */

  fun runPreprocessor (): Boolean
  {
    /**************************************************************************/
    var preprocessorPrefix = ConfigData["stepRunnablePreprocessorCommandPrefix"] ?: ""
    var preprocessorFilePath = ConfigData["stepRunnablePreprocessorFilePath"] ?: return false
    preprocessorFilePath = StandardFileLocations.getInputPath(preprocessorFilePath, null) // Expand out things like $root etc.

    val command: MutableList<String> = ArrayList()
    if (preprocessorPrefix.isNotEmpty())
      command.add(preprocessorPrefix.trim())
    else when (File(preprocessorFilePath).extension.lowercase())
    {
      "js" ->
      {
        command.add("node")
        command.add("\"$preprocessorFilePath\"")
      }


      "py" ->
      {
        command.add("python")
        command.add("\"$preprocessorFilePath\"")
      }


      "jar" ->
      {
        command.add("java"); command.add("-jar")
        val mainClass = Zip.getInputStream(preprocessorFilePath, "META-INF/MANIFEST.MF")!!.first.bufferedReader().readLines().first { it.contains("Main-Class") }.split(" ")[1]
        command.add(mainClass) // Not sure we actually need this.
        command.add("\"$preprocessorFilePath\"")
      }
    }



    /**************************************************************************/
    val booksToBeProcessed = Dbg.getBooksToBeProcessed().joinToString(".")
    command.add(StandardFileLocations.getPreprocessedUsxFolderPath())
    command.add(StandardFileLocations.getRawInputFolderPath())
    if (booksToBeProcessed.isNotEmpty()) command.add(booksToBeProcessed)
    runCommand("  Preprocessing: ", command)
    Dbg.reportProgress("Preprocessing complete", 1)
    return true
  }


  /****************************************************************************/
  /**
  * Called during the converter's validation phase.  Validation entails
  * comparing the canonical content of verses as they stand in the enhanced
  * USX with the content in the 'raw' USX.  This is fine where there has been no
  * preprocessing or where we've used a standalone preprocessor, because in the
  * latter case, the converter treats the preprocessed text as being the raw
  * text.  But if we have a callable preprocessor which has modified the text,
  * we have a problem, because we need to know the result of the modification.
  * The present method is called with the genuine raw USX.  Where you have been
  * using a callable preprocessor, it needs to return what would have been the
  * result of applying the preprocessor to that piece of text.
  *
  * @param text Text to be processed.
  * @return Original text or modified text as appropriate.
  */

  fun getTextForValidation (text: String): String
  {
    return if (null == m_MethodGetTextForValidation) text else m_MethodGetTextForValidation!!.invoke(m_PreprocessorInstance!!, text) as String
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private var m_CheckedExistence = false
  private var m_MethodGetTextForValidation: Method? = null
  private var m_MethodPreprocess: Method? = null
  private var m_PreprocessorInstance: Any? = null



  /****************************************************************************/
  private fun initialise ()
  {
    /**************************************************************************/
    m_CheckedExistence = true
    var jarPath = ConfigData["stepCallablePreprocessorFilePath"] ?: return
    jarPath = StandardFileLocations.getInputPath(jarPath, null)



    /**************************************************************************/
    if (!File(jarPath).exists()) throw StepException("Couldn't find callable JAR pre-processor: $jarPath.")



    /**************************************************************************/
    try
    {
      val jarUrl = File(jarPath).toURI().toURL()
      val classLoader = URLClassLoader(arrayOf(jarUrl), Thread.currentThread().contextClassLoader)
      val loadedClass = classLoader.loadClass("org.stepbible.preprocessor.Preprocessor")
      m_PreprocessorInstance = loadedClass.getDeclaredConstructor().newInstance()
      val classToLoad = Class.forName("org.stepbible.preprocessor.Preprocessor", true, classLoader)
      m_MethodPreprocess = classToLoad.getDeclaredMethod("preprocess", Document::class.java)
      try { m_MethodGetTextForValidation = classToLoad.getDeclaredMethod("getTextForValidation", String::class.java) } catch (_: Exception) {}
    }
    catch (e: Exception)
    {
      throw StepException("Failed to initialise callable preprocessor: ${e.message}")
    }
  }
}