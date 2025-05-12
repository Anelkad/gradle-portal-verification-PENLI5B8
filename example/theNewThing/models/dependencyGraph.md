
%% Dependents
:example:app-.->:example:ui
:example:app-.->:example:domain
:example:app-.->:example:theNewThing:feature
:example:app-.->:example:thePremiumThing:feature
:example:theNewThing:data-.->:example:models
:example:theNewThing:data-.->:example:theNewThing:models
:example:theNewThing:feature-.->:example:theNewThing:ui
:example:theNewThing:feature-.->:example:theNewThing:domain
:example:theNewThing:ui-.->:example:models
:example:theNewThing:ui-.->:example:theNewThing:models
:example:theNewThing:ui-.->:example:shared-ui
:example:theNewThing:domain-.API.->:example:theNewThing:models
:example:theNewThing:domain-.->:example:theNewThing:data
```