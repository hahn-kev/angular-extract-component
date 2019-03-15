import org.angular2.lang.html.psi.Angular2HtmlBananaBoxBinding;

public class Angular2WayBinding extends AngularBinding {
    public Angular2HtmlBananaBoxBinding bananaBoxBinding;

    public Angular2WayBinding(Angular2HtmlBananaBoxBinding element) {
        super(element.getBinding(), true);
        bananaBoxBinding = element;
    }

    public InputField getInputField() {
        return inputFields.iterator().next();
    }
}
