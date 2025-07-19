package arnold.fucaptcha.omniparser.models;

public enum ProcessState {
    ONGOING("Indicating that there still are actions to take before the acceptance criteria is met."),
    DONE("Set to `DONE` *only* if actions were taken, and the result is evidently (supported by visual evidence or indicators, ex: once the button is clicked, a text can be seen confirming that the button was clicked. If no such indicator is seen, process cannot be DONE) satisfying the acceptance criteria");

    final String stateDescription;

    ProcessState(String description){
        this.stateDescription = description;
    }
}