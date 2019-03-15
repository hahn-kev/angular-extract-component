import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.JSElementFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class RefactorHelper {
    private final Project project;
    private final PsiElement rootElement;

    public RefactorHelper(Project project, PsiElement rootElement) {

        this.project = project;
        this.rootElement = rootElement;
    }

    public void DoRefactor() {
        if (rootElement == null) return;
        String componentName = Messages.showInputDialog(project, "Component name", "Component Name", Messages.getQuestionIcon(), "test", null);
        if (componentName == null) return;

        PsiFile containingFile = rootElement.getContainingFile();
        PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        PsiElement elementNew = rootElement.copy();

        Set<AngularBinding> bindings = new HashSet<>();

        Set<AngularEvent> actions = new HashSet<>();

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


        StringBuilder htmlBuilder = InvokeTemplate(componentName, bindings, actions);
        rootElement.replace(XmlElementFactory.getInstance(project).createTagFromText(htmlBuilder.toString(), Angular2HtmlLanguage.INSTANCE));

        StringBuilder jsBuilder = RenderComponentJs(componentName, bindings, actions);
        PsiFile newTs = PsiFileFactory.getInstance(project).createFileFromText(componentName + ".component.ts", TypeScriptFileType.INSTANCE, jsBuilder.toString());
        containingDirectory.add(newTs);

        String componentHtml = RenderComponentHtml(elementNew, bindings, actions);
        PsiFile newHtml = PsiFileFactory.getInstance(project)
                .createFileFromText(componentName + ".component.html", Angular2HtmlLanguage.INSTANCE, componentHtml);
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

    private String RenderComponentHtml(PsiElement element, Set<AngularBinding> bindings, Set<AngularEvent> actions) {
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
        return element.getText();
    }

    @NotNull
    private StringBuilder RenderComponentJs(String componentName, Set<AngularBinding> bindings, Set<AngularEvent> events) {
        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append("import{Component");

        if (!bindings.isEmpty()) jsBuilder.append(",Input");
        if (!events.isEmpty() || bindings.stream().anyMatch(AngularBinding::isTwoWayBinding))
            jsBuilder.append(",Output,EventEmitter");
        jsBuilder.append("}from'@angular/core';\n");
        jsBuilder.append("@Component({selector:'app-").append(componentName).append("', templateUrl:'./").append(componentName).append(".component.html',styles:[]})\n");
        jsBuilder.append("export class ").append(componentName).append("Component{\n");
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

    private boolean InRefactorScope(PsiElement element) {
        return rootElement.getTextRange().contains(element.getTextOffset());
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
