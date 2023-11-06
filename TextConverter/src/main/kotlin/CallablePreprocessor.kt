package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader

/******************************************************************************/
/**
 * Handles callable preprocessors.
 *
 * There are two ways in which USX can be preprocessed to address issues
 * specific to an individual Bible text.
 *
 * One is to provide a standalone program (JAR, Javascript file, .exe, .py,
 * .bat) which runs over all of the USX files before the main processing starts
 * and updates them as necessary, creating new versions.  This approach is
 * probably easier from the point of view of debugging, but involves more
 * file-io and more copies of files lying around
 *
 * The other -- implemented here -- is to have a specially-named JAR file
 * (callablePreprocessor.jar) in the Preprocessor folder for the text.  This
 * must contain a single class -- Preprocessor -- in the package
 * org.stepbible.preprocessor, and the class must implement the method
 *
 *   fun preprocess (doc: Document): List<String>?
 *
 * which updates the document as necessary and returns a list of errors /
 * warnings / informationals, each of the form:
 *
 *   ERROR: blah   OR   WARNING: blah   OR   INFORMATION: blah
 *
 *
 * If the relevant JAR can be located, and if it is valid (ie has the
 * correct class and method in the correct package), it is invoked here
 * in the *process* method to update the document passed to it.  Any
 * messages are returned to the caller.
 *
 *
 *
 *
 * ## Creating the preprocessor
 *
 * - Make a Kotlin project for the preprocessor.  Have IDEA create the sample
 *   Main for you -- we don't need the main function for the processing, but
 *   without it, IDEA seems to go wrong: syntax highlighting doesn't work, and
 *   when you build the JAR as an artifact, the relevant code doesn't end up in
 *   it.
 *
 * - In the generated file, create a class called Preprocessor, with contents
 *   something like the following:
 *
 *      package org.stepbible.preprocessor
 *
 *      import org.w3c.dom.*
 *
 *      fun main(args: Array<String>) { }
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
 *
 *   preprocess should apply any changes to the document.  It can also return
 *   errors, warnings, etc in the return value.  Each entry in the returned
 *   list should start 'ERROR: ', @WARNING: ' or 'INFORMATION: '.
 *
 *   getTextForValidation is required only if preprocess has done anything to
 *   change the text content.  The text converter compares the text contents
 *   verses in the enhanced USX with what it has for those same verses in the
 *   non-enhanced version.  If 'preprocess' has intervened to change the text
 *   content before conversion proper (and it probably will have done), then
 *   getTextForValidation should take the original raw text and return a
 *   revised version which will be the same as the enhanced version now has.
 *   If it is difficult or impossible to do this (because the changes are too
 *   significant) you are better off not using a callable preprocessor.
 *   Instead you should use one of the alternative forms of preprocessing, in
 *   which revised USX files are generated before the converter proper ever
 *   runs.  That way, it believes its input was these revised files, which
 *   removes the need to simulate the revised input.
 *
 *   You can, of course, add more internal methods in order to carry out the
 *   processing.
 *
 *   CAUTION: I AM NOT SURE THE FOLLOWING INSTRUCTIONS ARE 100% CORRECT.
 *
 * - Go to File/ProjectStructure/Artifacts, hit the '+' at the top left and then
 *   select JAR and then FromModulesWithDependencies.
 *
 * - In the resulting window ... Against Modules, select the item with ‘.main’
 *   against it.  Set MainClass (even though we're never going to use the main
 *   method).  Make sure ExtractToTheTargetJar is selected.
 *
 * - In the new window, change 'Name' to callablePreprocessor:jar.  (This
 *   doesn’t seem to do much, other than alter the name of the folder where the
 *   output will be stored, but it seems neater.)  Click right on the name of
 *   the JAR file in the detail window lower down, and change it to
 *   callablePreprocessor.jar.
 *
 * - Build the artifact.  It will turn up in the 'out' folder, from where you
 *   can copy it to the Preprocessor folder under your text root.
 *
 * - Don't forget to rebuild it and copy it again each time you need to change
 *   it.
 *
 * @author ARA Jamieson
 */

object CallablePreprocessor
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun getTextForValidation (text: String): String
  {
    return if (null == m_MethodGetTextForValidation) text else m_MethodGetTextForValidation!!.invoke(m_PreprocessorInstance!!, text) as String
  }


  /****************************************************************************/
  fun process (doc: Document): List<String>?
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
    var jarPath = ConfigData["stepCallablePreprocessorJar"] ?: return
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