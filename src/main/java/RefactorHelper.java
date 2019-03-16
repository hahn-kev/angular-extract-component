import com.google.common.base.CaseFormat;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.JSElementFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlAttribute;
import org.angular2.lang.expr.psi.Angular2Interpolation;
import org.angular2.lang.expr.psi.Angular2RecursiveVisitor;
import org.angular2.lang.expr.psi.Angular2TemplateBinding;
import org.angular2.lang.html.Angular2HtmlLanguage;
import org.angular2.lang.html.psi.Angular2HtmlEvent;
import org.angular2.lang.html.psi.Angular2HtmlPropertyBinding;
import org.angular2.lang.html.psi.Angular2HtmlRecursiveElementVisitor;
import org.angular2.lang.html.psi.impl.Angular2HtmlBananaBoxBindingImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefactorHelper {
    private final Project project;
    private final PsiElement[] rootElements;

    public RefactorHelper(Project project, PsiElement rootElement) {
        this.project = project;
        this.rootElements = rootElement == null ? new PsiElement[0] : new PsiElement[]{rootElement};
    }

    public RefactorHelper(Project project, PsiElement[] rootElements) {
        this.project = project;
        this.rootElements = rootElements;
    }

    public void DoRefactor() {
        if (rootElements.length == 0) return;
        String componentNameCamelCase = Messages.showInputDialog(project, "Component name (in upper camel case)", "Component Name", Messages.getQuestionIcon());
        if (componentNameCamelCase == null) return;
        componentNameCamelCase = StringUtil.capitalize(componentNameCamelCase);

        PsiFile containingFile = rootElements[0].getContainingFile();
        PsiDirectory containingDirectory = containingFile.getContainingDirectory();

        Set<AngularBinding> bindings = new HashSet<>();

        Set<AngularEvent> actions = new HashSet<>();
        List<PsiElement> newElements = new ArrayList<>();

        for (PsiElement rootElement : rootElements) {
            PsiElement elementNew = rootElement.copy();
            newElements.add(elementNew);
            VisitElement(elementNew, bindings, actions);
        }

        String componentHyphen = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, componentNameCamelCase);
        StringBuilder htmlBuilder = InvokeTemplate(componentHyphen, bindings, actions);
        CleanupOtherElements();
        rootElements[0].replace(XmlElementFactory.getInstance(project).createTagFromText(htmlBuilder.toString(), Angular2HtmlLanguage.INSTANCE));
        StringBuilder jsBuilder = RenderComponentJs(componentNameCamelCase, componentHyphen, bindings, actions);
        PsiFile newTs = PsiFileFactory.getInstance(project).createFileFromText(componentHyphen + ".component.ts", TypeScriptFileType.INSTANCE, jsBuilder.toString());
        containingDirectory.add(newTs);

        String componentHtml = RenderComponentHtml(newElements, bindings, actions);
        PsiFile newHtml = PsiFileFactory.getInstance(project)
                .createFileFromText(componentHyphen + ".component.html", Angular2HtmlLanguage.INSTANCE, componentHtml);
        containingDirectory.add(newHtml);

        CodeStyleManager.getInstance(project).reformat(newHtml);
        CodeStyleManager.getInstance(project).reformat(newTs);
        Module module = ModuleUtil.findModuleForFile(containingFile);
        if (module != null) {
            ModuleRootModificationUtil.addContentRoot(module, newHtml.getVirtualFile());
            ModuleRootModificationUtil.addContentRoot(module, containingDirectory.getVirtualFile().getPath() + "\\" + newTs.getName());
        }
        VirtualFileManager.getInstance().asyncRefresh(null);
    }

    private void CleanupOtherElements() {
        if (rootElements.length == 1) return;
        PsiElement secondElement = rootElements[0].getNextSibling();
        PsiElement lastElement = rootElements[rootElements.length - 1];
        secondElement.getParent().deleteChildRange(secondElement, lastElement);
    }

    private void VisitElement(PsiElement elementNew, Set<AngularBinding> bindings, Set<AngularEvent> actions) {
        elementNew.acceptChildren(new Angular2HtmlRecursiveElementVisitor() {
            @Override
            public void visitBananaBoxBinding(Angular2HtmlBananaBoxBindingImpl bananaBoxBinding) {
                bindings.add(new Angular2WayBinding(bananaBoxBinding));
            }

            @Override
            public void visitEvent(Angular2HtmlEvent event) {
                actions.add(new AngularEvent(event.getAction()));
            }

            @Override
            public void visitPropertyBinding(Angular2HtmlPropertyBinding propertyBinding) {
                bindings.add(new AngularBinding(propertyBinding.getBinding(), false));
            }

//            @Override
//            public void visitVariable(Angular2HtmlVariable variable) {
//                super.visitVariable(variable);
//            }

//            @Override
//            public void visitTemplateBindings(Angular2HtmlTemplateBindingsImpl bindings) {
//                super.visitTemplateBindings(bindings);
//            }

//            @Override
//            public void visitReference(Angular2HtmlReference reference) {
            //do something with html references the #name kind
//            }
//
//            @Override
//            public void visitBoundAttribute(Angular2HtmlBoundAttribute boundAttribute) {
//                super.visitBoundAttribute(boundAttribute);
//            }
//
//            @Override
//            public void visitExpansionForm(Angular2HtmlExpansionForm expansion) {
//                super.visitExpansionForm(expansion);
//            }
//
//            @Override
//            public void visitExpansionFormCase(Angular2HtmlExpansionFormCaseImpl expansionCase) {
//                super.visitExpansionFormCase(expansionCase);
//            }
        });

        elementNew.acceptChildren(new Angular2RecursiveVisitor() {
            @Override
            public void visitAngular2Interpolation(Angular2Interpolation interpolation) {
                bindings.add(new AngularBinding(interpolation, false));
            }

            @Override
            public void visitAngular2TemplateBinding(Angular2TemplateBinding templateBinding) {

                bindings.add(new AngularBinding(templateBinding, false));
            }
        });
    }

    @NotNull
    private StringBuilder InvokeTemplate(String componentName, Collection<AngularBinding> bindings, Set<AngularEvent> events) {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<app-").append(componentName);
        AddInvokeInputReferences(bindings, htmlBuilder);
        AddInvokeEventReferences(events, htmlBuilder);
        htmlBuilder.append(">");
        htmlBuilder.append("</app-").append(componentName).append(">");
        return htmlBuilder;
    }

    private void AddInvokeInputReferences(Collection<AngularBinding> bindings, StringBuilder htmlBuilder) {
        TransformForRendering(bindings)
                .forEach(inputField -> {
                    if (inputField.isTwoWay) {
                        htmlBuilder.append(" [(").append(inputField.fieldName).append(")]=");
                    } else {
                        htmlBuilder.append(" [").append(inputField.fieldName).append("]=");
                    }
                    htmlBuilder.append('"').append(inputField.originalBody).append('"');
                });
    }

    private Stream<InputField> TransformForRendering(Collection<AngularBinding> bindings) {
        //sorted so 2 way bindings come first
        return bindings.stream().sorted((o1, o2) -> Boolean.compare(o2.isTwoWayBinding, o1.isTwoWayBinding)).flatMap(angularBinding -> angularBinding.inputFields.stream()).distinct();
    }

    private void AddInvokeEventReferences(Set<AngularEvent> events, StringBuilder htmlBuilder) {
        for (AngularEvent event : events) {
            htmlBuilder.append(" (").append(event.eventName).append(")=");
            htmlBuilder.append('"').append(event.action.getText()).append('"');
        }
    }

    private String RenderComponentHtml(List<PsiElement> element, Set<AngularBinding> bindings, Set<AngularEvent> actions) {
        bindings.stream().flatMap(angularBinding -> angularBinding.callExpressions.stream()).forEach(jsCallExpression -> {
            JSElementFactory.replaceExpression(jsCallExpression, AngularBinding.callExpressionFieldName(jsCallExpression));
        });
        bindings.stream().filter(AngularBinding::isTwoWayBinding).forEach(angularBinding -> {
            Angular2WayBinding angular2WayBinding = (Angular2WayBinding) angularBinding;
            InputField inputField = angular2WayBinding.getInputField();
            String attributeName = String.format("(%sChange)", angular2WayBinding.bananaBoxBinding.getPropertyName());
            String value = inputField.fieldName + "Change.emit($event)";
            XmlAttribute attribute = XmlElementFactory.getInstance(project).createAttribute(attributeName, value, angular2WayBinding.bananaBoxBinding);
            angular2WayBinding.bananaBoxBinding.getParent().addAfter(attribute, angular2WayBinding.bananaBoxBinding);
        });
        return element.stream().map(PsiElement::getText).collect(Collectors.joining(""));
    }

    @NotNull
    private StringBuilder RenderComponentJs(String componentNameCamelCase, String componentHyphen, Set<AngularBinding> bindings, Set<AngularEvent> events) {
        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append("import{Component");

        if (!bindings.isEmpty()) jsBuilder.append(",Input");
        if (!events.isEmpty() || bindings.stream().anyMatch(AngularBinding::isTwoWayBinding))
            jsBuilder.append(",Output,EventEmitter");
        jsBuilder.append("}from'@angular/core';\n");
        jsBuilder.append("@Component({selector:'app-").append(componentHyphen).append("', templateUrl:'./").append(componentHyphen).append(".component.html',styles:[]})\n");
        jsBuilder.append("export class ").append(componentNameCamelCase).append("Component{\n");
        AddJsInputReferences(bindings, jsBuilder);
        AddJsEvents(events, jsBuilder);
        jsBuilder.append("}");
        return jsBuilder;
    }

    private void AddJsInputReferences(Set<AngularBinding> bindings, StringBuilder jsBuilder) {
        TransformForRendering(bindings).forEach(inputField -> {
            if (inputField.isTwoWay) {
                AddJs2WayBinding(jsBuilder, inputField);
            } else {
                AddJsInput(jsBuilder, inputField);
            }
        });
    }

    private void AddJsInput(StringBuilder jsBuilder, InputField inputField) {
        jsBuilder.append("@Input()").append(inputField.fieldName);
        if (inputField.fieldType != null) jsBuilder.append(":").append(inputField.fieldType);
        jsBuilder.append(";\n");
    }

    private void AddJs2WayBinding(StringBuilder jsBuilder, InputField inputField) {
        jsBuilder.append("@Output()").append(inputField.fieldName).append("Change");
        jsBuilder.append("=new EventEmitter<").append(inputField.fieldType).append(">()");
        jsBuilder.append(";\n");
        AddJsInput(jsBuilder, inputField);
    }

    private void AddJsEvents(Set<AngularEvent> callExpressions, StringBuilder jsBuilder) {
        for (AngularEvent event : callExpressions) {
            AddJsEvent(jsBuilder, event.eventName);
        }
    }

    private void AddJsEvent(StringBuilder jsBuilder, String eventName) {
        jsBuilder.append("@Output(\"").append(eventName).append("\")").append(eventName).append("Event");
        jsBuilder.append("=new EventEmitter<").append("void").append(">()");
        jsBuilder.append(";\n");
        jsBuilder.append(eventName).append("() {");
        jsBuilder.append("this.").append(eventName).append("Event.emit();}\n");
    }
}
