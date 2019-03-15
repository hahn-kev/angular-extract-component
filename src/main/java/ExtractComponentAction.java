import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.angular2.lang.html.Angular2HtmlLanguage;
import org.jetbrains.annotations.NotNull;

public class ExtractComponentAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        Project currentProject = anActionEvent.getProject();
        WriteCommandAction.runWriteCommandAction(currentProject, () -> {
            Project project = anActionEvent.getProject();
            if (project == null) return;
            PsiElement element = anActionEvent.getData(CommonDataKeys.PSI_ELEMENT);
            RefactorHelper refactorHelper = new RefactorHelper(project, element);
            refactorHelper.DoRefactor();
        });
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement == null) return;
        boolean isHtmlLanguage = psiElement.getLanguage().is(Angular2HtmlLanguage.INSTANCE);
        e.getPresentation().setEnabledAndVisible(isHtmlLanguage);
    }
}
