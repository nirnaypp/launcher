package xsbt.boot

import Pre._
import java.io.{File, FileFilter}
import java.net.{URL, URLClassLoader}
import java.util.concurrent.Callable

trait Provider extends NotNull
{
	def configuration: UpdateConfiguration
	def baseDirectories: List[File]
	def testLoadClasses: List[String]
	def target: UpdateTarget
	def failLabel: String
	def parentLoader: ClassLoader
	def lockFile: File

	def classpath = Provider.getJars(baseDirectories)
		
	def retrieveFailed: Nothing = fail("")
	def retrieveCorrupt(missing: Iterable[String]): Nothing = fail(": missing " + missing.mkString(", "))
	private def fail(extra: String) =
		throw new xsbti.RetrieveException(versionString, "Could not retrieve " + failLabel + extra)
	private def versionString: String = target match { case UpdateScala => configuration.scalaVersion; case a: UpdateApp => Version.get(a.id.version) }

	val (jars, loader) = Locks(lockFile, new initialize)
	private final class initialize extends Callable[(Array[File], ClassLoader)]
	{
		def call =
		{
			val (existingJars, existingLoader) = createLoader
			if(Provider.getMissing(existingLoader, testLoadClasses).isEmpty)
				(existingJars, existingLoader)
			else
			{
				val retrieveSuccess = ( new Update(configuration) )(target)
				if(retrieveSuccess)
				{
					val (newJars, newLoader) = createLoader
					val missing = Provider.getMissing(newLoader, testLoadClasses)
					if(missing.isEmpty) (newJars, newLoader) else retrieveCorrupt(missing)
				}
				else
					retrieveFailed
			}
		}
		def createLoader =
		{
			val jars = classpath
			(jars, new URLClassLoader(jars.map(_.toURI.toURL), parentLoader) )
		}
	}
}

object Provider
{
	def getJars(directories: List[File]): Array[File] = toArray(directories.flatMap(directory => wrapNull(directory.listFiles(JarFilter))))
	def wrapNull(a: Array[File]): Array[File] = if(a == null) Array() else a

	object JarFilter extends FileFilter
	{
		def accept(file: File) = !file.isDirectory && file.getName.endsWith(".jar")
	}
	def getMissing(loader: ClassLoader, classes: Iterable[String]): Iterable[String] =
	{
		def classMissing(c: String) = try { Class.forName(c, false, loader); false } catch { case e: ClassNotFoundException => true }
		classes.toList.filter(classMissing)
	}
}