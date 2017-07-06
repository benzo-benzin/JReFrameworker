package jreframeworker.builder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.objectweb.asm.tree.ClassNode;
import org.xml.sax.SAXException;

import jreframeworker.common.RuntimeUtils;
import jreframeworker.core.BuildFile;
import jreframeworker.core.JReFrameworker;
import jreframeworker.core.JReFrameworkerProject;
import jreframeworker.engine.Engine;
import jreframeworker.engine.identifiers.DefineFinalityIdentifier;
import jreframeworker.engine.identifiers.DefineIdentifier;
import jreframeworker.engine.identifiers.DefineIdentifier.DefineTypeAnnotation;
import jreframeworker.engine.identifiers.DefineVisibilityIdentifier;
import jreframeworker.engine.identifiers.MergeIdentifier;
import jreframeworker.engine.identifiers.MergeIdentifier.MergeTypeAnnotation;
import jreframeworker.engine.identifiers.PurgeIdentifier;
import jreframeworker.engine.utils.BytecodeUtils;
import jreframeworker.log.Log;
import jreframeworker.ui.PreferencesPage;

public class JReFrameworkerBuilder extends IncrementalProjectBuilder {
	
	private static int buildNumber = 1;
	
	public static final String BUILDER_ID = "jreframeworker.JReFrameworkerBuilder";

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@SuppressWarnings("rawtypes")
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor) throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	protected void clean(IProgressMonitor monitor) throws CoreException {
		JReFrameworkerProject jrefProject = getJReFrameworkerProject();
		if(jrefProject != null){
			monitor.beginTask("Cleaning: " + jrefProject.getProject().getName(), 1);
			Log.info("Cleaning: " + jrefProject.getProject().getName());
			
			// clear the Java compiler error markers (these will be fixed and restored if they remain after building phases)
			// TODO: is this actually working?
			jrefProject.getProject().deleteMarkers(JavaCore.ERROR, true, IProject.DEPTH_INFINITE);
			jrefProject.getProject().deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IProject.DEPTH_INFINITE);

			jrefProject.disableJavaBuilder();
			try {
				jrefProject.clean();
				jrefProject.restoreOriginalClasspathEntries(); 
			} catch (Exception e) {
				Log.error("Error cleaning " + jrefProject.getProject().getName(), e);
			}
			jrefProject.enableJavaBuilder();
			
			this.forgetLastBuiltState();
			jrefProject.refresh();
			
			monitor.worked(1);
		} else {
			Log.warning(getProject().getName() + " is not a valid JReFrameworker project!");
		}
	}
	
	protected void fullBuild(final IProgressMonitor monitor) throws CoreException {
//		Log.info("JReFrameworker Build Number: " + buildNumber++);
//		JReFrameworkerProject jrefProject = getJReFrameworkerProject();
//		
//		if(jrefProject != null) {
//			// run the java builder
//			jrefProject.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
//			jrefProject.refresh();
//			
//			// make sure the build directory exists
//			File projectBuildDirectory = jrefProject.getBinaryDirectory();
//			if (!projectBuildDirectory.exists()) {
//				projectBuildDirectory.mkdirs();
//			}
//			
//			// build each jref project fresh
//			monitor.beginTask("Building: " + jrefProject.getProject().getName(), 1);
//			Log.info("Building: " + jrefProject.getProject().getName());
//			
////			// add each class from classes in jars in raw directory
////			// this happens before any build phases
////			File rawDirectory = jrefProject.getProject().getFolder(JReFrameworker.RAW_DIRECTORY).getLocation().toFile();
////			if(rawDirectory.exists()){
////				for(File jarFile : rawDirectory.listFiles()){
////					if(jarFile.getName().endsWith(".jar")){
////						for(Engine engine : allEngines){
////							Log.info("Embedding raw resource: " + jarFile.getName() + " in " + engine.getJarName());
////							
////							// TODO: unjar to temp directory instead?
////							File outputDirectory = new File(jarFile.getParentFile().getAbsolutePath() + File.separatorChar + "." + jarFile.getName().replace(".jar", ""));
////							JarModifier.unjar(jarFile, outputDirectory);
////							
////							// add raw class files
////							addClassFiles(engine, outputDirectory);
////							
////							// cleanup temporary directory
////							delete(outputDirectory);
////						}
////					}
////				}
////			}
//			
//			// a set of processed class files, a set of class files to be
//			// processed, and a set of class files that cannot be processed yet
//			// because they have compilation errors
//			// class files with compilation errors can still be produced but for
//			// our purposes should not be used
//			// Reference: https://eclipse.org/articles/Article-Builders/builders.html
//			Set<File> processedClassFiles = new HashSet<File>();
//			Set<File> classFilesToProcess = new HashSet<File>();
//			Set<File> unresolvedClassFiles = new HashSet<File>();
//			
//			// discover class files to process and filter out
//			// the compilation units with build errors
//			ICompilationUnit[] compilationUnits = BuilderUtils.getSourceCompilationUnits(jrefProject.getJavaProject());
//			for(ICompilationUnit compilationUnit : compilationUnits){
//				try {
//					File sourceFile = compilationUnit.getCorrespondingResource().getLocation().toFile().getCanonicalFile();
//					File classFile = BuilderUtils.getCorrespondingClassFile(jrefProject, compilationUnit);
//					if(classFile.exists()){
//						if(BuilderUtils.hasTypeModification(classFile)){
//							if(BuilderUtils.hasSevereProblems(compilationUnit)){
//								unresolvedClassFiles.add(classFile);
//							} else {
//								classFilesToProcess.add(classFile);
//							}
//						}
//					}
//				} catch (IOException e) {
//					Log.error("Error resolving compilation units", e);
//					return;
//				}
//			}
//			
////			// process class files until no new class files are discovered
////			while(!classFilesToProcess.isEmpty()){
////			
////				// TODO: implement
////				
////				// detect build phases and build in order
////				
////				// check that the phases haven't regressed
////				
////				// run java builder again...
////				
////				// remove the classes we have processed
////				
////				// figure out what new classes we have found and what previously unresolved classes can now be processed
////				
////			}
//			
//			// discover the build phases
//			Map<Integer,Integer> phases = null;
//			try {
//				phases = BuilderUtils.getNormalizedBuildPhases(classFilesToProcess);
//				String phasePurality = phases.size() > 1 || phases.isEmpty() ? "s" : "";
//				Log.info("Discovered " + phases.size() + " explicit build phase" + phasePurality + "\nNormalized Build Phase Mapping: " + phases.toString());
//				if(phases.isEmpty()){
//					phases.put(1, 1); // added implicit build phase
//				}
//			} catch (Exception e){
//				Log.error("Error determining project build phases", e);
//				return;
//			}
//	
//			try {
//				LinkedList<Integer> sortedPhases = new LinkedList<Integer>(phases.keySet());
//				Collections.sort(sortedPhases);
//				int lastPhase = -1;
//				int lastNamedPhase = -1;
//				
//				for(int currentPhase : sortedPhases){
//					int currentNamedPhase = phases.get(currentPhase);
//					boolean isFirstPhase = false;
//					if(sortedPhases.getFirst().equals(currentPhase)){
//						isFirstPhase = true;
//					}
//					boolean isLastPhase = false;
//					if(sortedPhases.getLast().equals(currentPhase)){
//						isLastPhase = true;
//					}
//					
//					// map class entries to and initial modification engine sets
//					Map<String, Set<Engine>> engineMap = new HashMap<String, Set<Engine>>();
//					Set<Engine> allEngines = new HashSet<Engine>();
//
//					// initialize the modification engines
//					// if its the first phase then we are just initializing with the original jars
//					// if its after the first phase then we are initializing with the last build phase jars
//					BuildFile buildFile = jrefProject.getBuildFile();
//					if(isFirstPhase){
//						for(BuildFile.Target target : buildFile.getTargets()) {
//							// classpath has been restored, these are all the original jars
//							File originalJar = RuntimeUtils.getClasspathJar(target.getName(), jrefProject);
//							if (originalJar != null && originalJar.exists()) {
//								Engine engine = new Engine(originalJar, PreferencesPage.getMergeRenamingPrefix());
//								allEngines.add(engine);
//								for(String entry : engine.getOriginalEntries()){
//									entry = entry.replace(".class", "");
//									if(engineMap.containsKey(entry)){
//										engineMap.get(entry).add(engine);
//									} else {
//										Set<Engine> engines = new HashSet<Engine>();
//										engines.add(engine);
//										engineMap.put(entry, engines);
//									}
//								}
//							} else {
//								Log.warning("Original Jar not found: " + target.getName());
//							}
//						}
//					} else {
//						for(BuildFile.Target target : buildFile.getTargets()) {
//							File phaseJar = BuilderUtils.getBuildPhaseJar(target.getName(), jrefProject, lastPhase, lastNamedPhase);
//							if(!phaseJar.exists()){
//								phaseJar = RuntimeUtils.getClasspathJar(target.getName(), jrefProject);
//							}
//							if (phaseJar != null && phaseJar.exists()) {
//								Engine engine = new Engine(phaseJar, PreferencesPage.getMergeRenamingPrefix());
//								allEngines.add(engine);
//								for(String entry : engine.getOriginalEntries()){
//									entry = entry.replace(".class", "");
//									if(engineMap.containsKey(entry)){
//										engineMap.get(entry).add(engine);
//									} else {
//										Set<Engine> engines = new HashSet<Engine>();
//										engines.add(engine);
//										engineMap.put(entry, engines);
//									}
//								}
//							} else {
//								Log.warning("Phase Jar not found: " + target.getName());
//							}
//						}
//					}
//					
//					// compute the source based jar modifications
//					buildProject(jrefProject.getBinaryDirectory(), jrefProject, engineMap, allEngines, currentPhase, currentNamedPhase);
//					
//					// write out the modified jars
//					for(Engine engine : allEngines){
//						File modifiedLibrary = BuilderUtils.getBuildPhaseJar(engine.getJarName(), jrefProject, currentPhase, currentNamedPhase);
//						modifiedLibrary.getParentFile().mkdirs();
//						engine.save(modifiedLibrary);
//
//						if(isLastPhase){
//							File finalModifiedLibrary = new File(projectBuildDirectory.getCanonicalPath() + File.separatorChar + engine.getJarName());
//							RuntimeUtils.copyFile(modifiedLibrary, finalModifiedLibrary);
//						}
//						
//						// log the modified runtime
//						String base = jrefProject.getProject().getLocation().toFile().getCanonicalPath();
//						String relativeFilePath = modifiedLibrary.getCanonicalPath().substring(base.length());
//						if(relativeFilePath.charAt(0) == File.separatorChar){
//							relativeFilePath = relativeFilePath.substring(1);
//						}
//						Log.info("Modified: " + relativeFilePath);
//					}
//					
//					// the current build  phase is over
//					lastPhase = currentPhase;
//					lastNamedPhase = currentNamedPhase;
//					if(currentPhase != currentNamedPhase){
//						Log.info("Phase " + currentPhase + " (identified as " + currentNamedPhase + ") completed.");
//					} else {
//						Log.info("Phase " + currentPhase + " completed.");
//					}
//					
//					jrefProject.refresh();
//					
//					// remove the java nature to prevent the Java builder from running until we are ready
//					// if the build phase directory is null or does not exist then nothing was done during the phase
//					File buildPhaseDirectory = BuilderUtils.getBuildPhaseDirectory(jrefProject, currentPhase, currentNamedPhase);
//					if(buildPhaseDirectory != null && buildPhaseDirectory.exists()){
//						jrefProject.disableJavaBuilder();
//						for(File file : buildPhaseDirectory.listFiles()){
//							if(file.getName().endsWith(".jar")){
//								File modifiedLibrary = file;
//								jrefProject.updateProjectLibrary(modifiedLibrary.getName(), modifiedLibrary);
//							}
//						}
//						// restore the java nature
//						jrefProject.enableJavaBuilder();
//						
//						jrefProject.refresh();
//					}
//				}
//			} catch (IOException | SAXException | ParserConfigurationException e) {
//				Log.error("Error building " + jrefProject.getProject().getName(), e);
//				return;
//			}
//
//			jrefProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
//			monitor.worked(1);
//		} else {
//			Log.warning(getProject().getName() + " is not a valid JReFrameworker project!");
//		}
	}

//	// TODO: adding a progress monitor subtask here would be a nice feature
//	private void buildProject(File binDirectory, JReFrameworkerProject jrefProject, Map<String, Set<Engine>> engineMap, Set<Engine> allEngines, int phase, int namedPhase) throws IOException {
//		// make changes for each annotated class file in current directory
//		File[] files = binDirectory.listFiles();
//		for(File file : files){
//			if(file.isFile()){
//				if(file.getName().endsWith(".class")){
//					byte[] classBytes = Files.readAllBytes(file.toPath());
//					if(classBytes.length > 0){
//						try {
//							// TODO: refactor this bit to just save the parsed annotation requests instead of true/false
//							ClassNode classNode = BytecodeUtils.getClassNode(classBytes);
//							boolean purgeModification = BuilderUtils.hasPurgeModification(classNode);
//							boolean finalityModification = BuilderUtils.hasFinalityModification(classNode);
//							boolean visibilityModification = BuilderUtils.hasVisibilityModification(classNode);
//							boolean mergeModification = BuilderUtils.hasMergeTypeModification(classNode);
//							boolean defineModification = BuilderUtils.hasDefineTypeModification(classNode);
//							
//							if(purgeModification || finalityModification || visibilityModification || mergeModification || defineModification){
//								// get the qualified modification class name
//								String base = jrefProject.getProject().getFolder(JReFrameworker.BINARY_DIRECTORY).getLocation().toFile().getCanonicalPath();
//								String modificationClassName = file.getCanonicalPath().substring(base.length());
//								if(modificationClassName.charAt(0) == File.separatorChar){
//									modificationClassName = modificationClassName.substring(1);
//								}
//								modificationClassName = modificationClassName.replace(".class", "");
//							
//								if(purgeModification){
//									Set<String> targets = PurgeIdentifier.getPurgeTargets(classNode, namedPhase);
//									for(String target : targets){
//										// purge target from each jar that contains the purge target
//										if(engineMap.containsKey(target)){
//											for(Engine engine : engineMap.get(target)){
//												if(RuntimeUtils.isRuntimeJar(engine.getOriginalJar())){
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader() });
//												} else {
//													URL[] jarURL = { new URL("jar:file:" + engine.getOriginalJar().getCanonicalPath() + "!/") };
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader(), URLClassLoader.newInstance(jarURL) });
//												}
//												engine.process(classBytes, phase, namedPhase);
//											}
//										} else {
//											Log.warning("Class entry [" + target + "] could not be found in any of the target jars.");
//										}
//									}
//								} 
//								
//								if(finalityModification){
//									Set<String> targets = DefineFinalityIdentifier.getFinalityTargets(classNode, namedPhase);
//									for(String target : targets){
//										// merge into each target jar that contains the merge target
//										if(engineMap.containsKey(target)){
//											for(Engine engine : engineMap.get(target)){
//												if(RuntimeUtils.isRuntimeJar(engine.getOriginalJar())){
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader() });
//												} else {
//													URL[] jarURL = { new URL("jar:file:" + engine.getOriginalJar().getCanonicalPath() + "!/") };
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader(), URLClassLoader.newInstance(jarURL) });
//												}
//												engine.process(classBytes, phase, namedPhase);
//											}
//										} else {
//											Log.warning("Class entry [" + target + "] could not be found in any of the target jars.");
//										}
//									}
//								} 
//								
//								if(visibilityModification){
//									Set<String> targets = DefineVisibilityIdentifier.getVisibilityTargets(classNode, namedPhase);
//									for(String target : targets){
//										// merge into each target jar that contains the merge target
//										if(engineMap.containsKey(target)){
//											for(Engine engine : engineMap.get(target)){
//												if(RuntimeUtils.isRuntimeJar(engine.getOriginalJar())){
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader() });
//												} else {
//													URL[] jarURL = { new URL("jar:file:" + engine.getOriginalJar().getCanonicalPath() + "!/") };
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader(), URLClassLoader.newInstance(jarURL) });
//												}
//												engine.process(classBytes, phase, namedPhase);
//											}
//										} else {
//											Log.warning("Class entry [" + target + "] could not be found in any of the target jars.");
//										}
//									}
//								}
//								
//								if(mergeModification){
//									MergeIdentifier mergeIdentifier = new MergeIdentifier(classNode);
//									MergeTypeAnnotation mergeTypeAnnotation = mergeIdentifier.getMergeTypeAnnotation();
//									if(mergeTypeAnnotation.getPhase() == namedPhase){
//										String target = mergeTypeAnnotation.getSupertype();
//										// merge into each target jar that contains the merge target
//										if(engineMap.containsKey(target)){
//											for(Engine engine : engineMap.get(target)){
//												if(RuntimeUtils.isRuntimeJar(engine.getOriginalJar())){
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader() });
//												} else {
//													URL[] jarURL = { new URL("jar:file:" + engine.getOriginalJar().getCanonicalPath() + "!/") };
//													engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader(), URLClassLoader.newInstance(jarURL) });
//												}
//												engine.process(classBytes, phase, namedPhase);
//											}
//										} else {
//											Log.warning("Class entry [" + target + "] could not be found in any of the target jars.");
//										}
//									}
//								} 
//								
//								if(defineModification){
//									DefineIdentifier defineIdentifier = new DefineIdentifier(classNode);
//									DefineTypeAnnotation defineTypeAnnotation = defineIdentifier.getDefineTypeAnnotation();
//									if(defineTypeAnnotation.getPhase() == namedPhase){
//										// define or replace in every target jar
//										for(Engine engine : allEngines){
//											if(RuntimeUtils.isRuntimeJar(engine.getOriginalJar())){
//												engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader() });
//											} else {
//												URL[] jarURL = { new URL("jar:file:" + engine.getOriginalJar().getCanonicalPath() + "!/") };
//												engine.setClassLoaders(new ClassLoader[]{ getClass().getClassLoader(), URLClassLoader.newInstance(jarURL) });
//											}
//											engine.process(classBytes, phase, namedPhase);
//										}
//									}
//								}
//							}
//						} catch (RuntimeException e){
//							Log.error("Error modifying jar...", e);
//						}
//					}
//				}
//			} else if(file.isDirectory()){
//				buildProject(file, jrefProject, engineMap, allEngines, phase, namedPhase);
//			}
//		}
//	}
	
	// TODO: see http://help.eclipse.org/mars/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Fguide%2FresAdv_builders.htm
	protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
		Log.info("Incremental Build");
		delta.accept(new BuildDeltaVisitor());
	}
	
	private static class BuildVisitor implements IResourceVisitor {
		@Override
		public boolean visit(IResource resource) {
			System.out.println(resource.getLocation().toFile().getAbsolutePath());
			return true;
		}
	}
	
	private static class BuildDeltaVisitor implements IResourceDeltaVisitor {
		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			File sourceFile = new File(delta.getFullPath().toOSString());
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				System.out.print("Added: " + sourceFile.getName());
				break;
			case IResourceDelta.REMOVED:
				System.out.print("Removed: " + sourceFile.getName());
				break;
			case IResourceDelta.CHANGED:
				System.out.print("Changed: " + sourceFile.getName());
				break;
			}
			return true;
		}
	}
	
	/**
	 * Returns the JReFrameworker project to build or clean, if the project is invalid returns null
	 * @return
	 */
	private JReFrameworkerProject getJReFrameworkerProject(){
		IProject project = getProject();
		try {
			if(project.isOpen() && project.exists() && project.hasNature(JavaCore.NATURE_ID) && project.hasNature(JReFrameworkerNature.NATURE_ID)){
				return new JReFrameworkerProject(project);
			}
		} catch (CoreException e) {}
		return null;
	}
	
	private void addClassFiles(Engine engine, File f) throws IOException {
		if (f.isDirectory()){
			for (File f2 : f.listFiles()){
				addClassFiles(engine, f2);
			}
		} else if(f.getName().endsWith(".class")){
			engine.addUnprocessed(Files.readAllBytes(f.toPath()), true);
		}
	}
	
}
