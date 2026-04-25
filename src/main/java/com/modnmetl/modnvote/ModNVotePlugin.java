// UPDATE ONLY builder renderer init line
// replace:
// this.pollBuilderRenderer = new PollBuilderRenderer();

// with:
this.pollBuilderRenderer = new PollBuilderRenderer(pollService);
