/*
 *  IntelliJ IDEA plugin to support the Neos CMS.
 *  Copyright (C) 2016  Christian Vette
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.vette.idea.neos.lang.fusion.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.indexing.FileBasedIndex;
import de.vette.idea.neos.indexes.DefaultContextFileIndex;
import de.vette.idea.neos.lang.fusion.psi.*;
import de.vette.idea.neos.lang.fusion.stubs.index.FusionNamespaceDeclarationIndex;
import de.vette.idea.neos.lang.fusion.stubs.index.FusionPrototypeDeclarationIndex;
import de.vette.idea.neos.util.PhpElementsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.php.lang.psi.elements.Method;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResolveEngine {
    protected final static Pattern RESOURCE_PATTERN =
            Pattern.compile("^resource://([^/]+)/(.*)");


    public static List<PsiElement> getPrototypeDefinitions(Project project, String name, @Nullable String namespace)
    {
        String instanceAliasNamespace = null;
        if (namespace != null) {
            instanceAliasNamespace = findNamespaceByAlias(project, namespace);
        }

        // find all prototypes that have the name of this instance
        List<PsiElement> result = new ArrayList<>();
        Collection<FusionPrototypeSignature> possiblePrototypes = StubIndex.getElements(
                FusionPrototypeDeclarationIndex.KEY,
                name,
                project,
                GlobalSearchScope.projectScope(project),
                FusionPrototypeSignature.class);

        // check for each prototype if the namespace matches by resolving aliases
        for (FusionPrototypeSignature possiblePrototype : possiblePrototypes) {
            FusionType prototypeType = possiblePrototype.getType();
            if (prototypeType != null) {
                PsiElement prototypeNamespace = prototypeType.getObjectTypeNamespace();
                if (prototypeNamespace != null) {
                    // check if prototype has default namespace
                    if (namespace == null) {
                        if (prototypeNamespace.getText().equals("TYPO3.Neos")
                                || prototypeNamespace.getText().equals("Neos.Neos")) {
                            result.add(possiblePrototype);
                        }
                        continue;
                    }

                    String prototypeNs = prototypeType.getObjectTypeNamespace().getText();
                    if (prototypeNs.equals(namespace) || prototypeNs.equals(instanceAliasNamespace)) {
                        result.add(possiblePrototype);
                    } else {
                        prototypeNs = findNamespaceByAlias(project, prototypeNs);
                        if (namespace.equals(prototypeNs)) {
                            result.add(possiblePrototype);
                        }
                    }
                } else if (namespace == null
                        || (namespace.equals("TYPO3.Neos")
                        || namespace.equals("Neos.Neos"))) {

                    result.add(possiblePrototype);
                }
            }
        }

        // If one of the results is a prototype inheritance, return it as the only result
        for (PsiElement resultPrototype : result) {
            if (resultPrototype.getParent() != null && resultPrototype.getParent() instanceof FusionPrototypeInheritance) {
                result.clear();
                result.add(resultPrototype);
                return result;
            }
        }

        return result;
    }

    @NotNull
    public static List<PsiElement> getPrototypeDefinitions(Project project, FusionType type) {
        if (type.getUnqualifiedType() == null) return new ArrayList<>();

        String instanceName = type.getUnqualifiedType().getText();
        String instanceNs = null;
        if (type.getObjectTypeNamespace() != null) {
            instanceNs = type.getObjectTypeNamespace().getText();
        }

        return ResolveEngine.getPrototypeDefinitions(project, instanceName, instanceNs);
    }

    @Nullable
    private static String findNamespaceByAlias(Project project, String alias) {
        Collection<FusionNamespaceDeclaration> namespaces = StubIndex.getElements(
                FusionNamespaceDeclarationIndex.KEY,
                alias,
                project,
                GlobalSearchScope.projectScope(project),
                FusionNamespaceDeclaration.class);

        if (!namespaces.isEmpty()) {
            FusionNamespace namespace = namespaces.iterator().next().getNamespace();
            if (namespace != null) {
                return namespace.getText();
            }
        }

        return null;
    }

    public static VirtualFile findResource(Project project, String resourcePath) {
        Matcher m = RESOURCE_PATTERN.matcher(resourcePath);
        if (m.matches()) {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(project, m.group(1), GlobalSearchScope.projectScope(project));
            VirtualFile baseDir = project.getBaseDir().findChild("Packages");
            if (baseDir == null) {
                return null;
            }

            resourcePath = "Resources/" + m.group(2);
            for (VirtualFile file : files) {
                if (file.getPath().startsWith(baseDir.getPath())) {
                    return file.findFileByRelativePath(resourcePath);
                }
            }
        }

        return null;
    }

    public static List<PsiElement> getEelHelpers(Project project, String name) {
        List<PsiElement> result = new ArrayList<>();
        List<String> helpers = FileBasedIndex.getInstance().getValues(DefaultContextFileIndex.KEY, name, GlobalSearchScope.allScope(project));
        for (String helper : helpers) {
            result.addAll(PhpElementsUtil.getClassInterfaceElements(project, helper));
        }

        return result;
    }

    public static List<PsiElement> getEelHelperMethods(Project project, String helperName, String methodName) {
        List<String> helpers = FileBasedIndex.getInstance().getValues(DefaultContextFileIndex.KEY, helperName, GlobalSearchScope.allScope(project));
        List<PsiElement> methods = new ArrayList<>();
        for (String helper : helpers) {
            Method method = PhpElementsUtil.getClassMethod(project, helper, methodName);
            if (method != null) {
                methods.add(method);
            }
        }

        return methods;
    }
}
