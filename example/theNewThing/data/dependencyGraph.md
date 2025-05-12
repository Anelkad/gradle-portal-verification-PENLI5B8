
%% Dependents
:example:app-.->:example:ui
:example:app-.->:example:domain
:example:app-.->:example:theNewThing:feature
:example:app-.->:example:thePremiumThing:feature
:example:theNewThing:feature-.->:example:theNewThing:ui
:example:theNewThing:feature-.->:example:theNewThing:domain
:example:theNewThing:domain-.API.->:example:theNewThing:models
:example:theNewThing:domain-.->:example:theNewThing:data
```