import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.angular2.lang.html.Angular2HtmlLanguage;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExtractComponentHandler implements RefactoringActionHandler, ElementsHandler {
    @Override
    public boolean isEnabledOnElements(PsiElement[] elements) {
        return Arrays.stream(elements).allMatch(psiElement -> psiElement.getLanguage().is(Angular2HtmlLanguage.INSTANCE));
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        String selectedText = editor.getSelectionModel().getSelectedText();
        if (StringUtils.isBlank(selectedText)) return;
        String selectedWithoutWhitespace = selectedText.trim();
        int startingWhitespace = selectedText.indexOf(selectedWithoutWhitespace);
        int endingWhitespace = Math.max(selectedText.length() - selectedWithoutWhitespace.length() - startingWhitespace, 0);

        PsiElement startElement = file.findElementAt(editor.getSelectionModel().getSelectionStart() + startingWhitespace);
        if (startElement == null) return;
        startElement = FindTopParentAtSameOffset(startElement);
        int selectionEnd = editor.getSelectionModel().getSelectionEnd() - (endingWhitespace + 1);
        PsiElement endElement = file.findElementAt(selectionEnd);
        if (endElement == null) return;
        endElement = FindTopParentAtSameOffsetFromEnd(endElement);

        if (startElement == null || startElement instanceof PsiFile || startElement.getTextOffset() > selectionEnd) {
            String message = RefactoringBundle
                    .getCannotRefactorMessage("Invalid Position");
            CommonRefactoringUtil.showErrorHint(project, editor, message, "Extract Component", null);
            return;
        }
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, startElement)) return;

        invoke(project, FindAllSiblings(startElement, endElement).toArray(new PsiElement[0]), dataContext);
    }

    private List<PsiElement> FindAllSiblings(PsiElement startElement, @Nullable PsiElement endElement) {
        List<PsiElement> elements = new ArrayList<>();
        elements.add(startElement);
        if (endElement == null) return elements;
        if (startElement == endElement) return elements;
        PsiElement nextSibling = startElement.getNextSibling();
        while (nextSibling != endElement && nextSibling != null) {
            elements.add(nextSibling);
            nextSibling = nextSibling.getNextSibling();
        }
        elements.add(endElement);
        return elements;
    }

    private PsiElement FindTopParentAtSameOffset(PsiElement psiElement) {
        int textOffset = psiElement.getTextOffset();
        while (psiElement.getParent().getTextOffset() == textOffset) {
            psiElement = psiElement.getParent();
        }
        return psiElement;
    }
    private PsiElement FindTopParentAtSameOffsetFromEnd(PsiElement psiElement) {
        int textOffset = psiElement.getTextOffset() + psiElement.getTextLength();
        PsiElement parent = psiElement.getParent();
        while (parent != null && parent.getTextOffset() + parent.getTextLength() == textOffset) {
            psiElement = psiElement.getParent();
            parent = psiElement.getParent();
        }
        return psiElement;
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            new RefactorHelper(project, elements).DoRefactor();
        });
    }
}
