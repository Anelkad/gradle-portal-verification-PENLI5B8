
%% Dependents
:example:app-.->:example:ui
:example:app-.->:example:domain
:example:app-.->:example:theNewThing:feature
:example:app-.->:example:thePremiumThing:feature
:example:ui-.->:example:models
:example:ui-.->:example:shared-ui
:example:theNewThing:feature-.->:example:theNewThing:ui
:example:theNewThing:feature-.->:example:theNewThing:domain
:example:theNewThing:ui-.->:example:models
:example:theNewThing:ui-.->:example:theNewThing:models
:example:theNewThing:ui-.->:example:shared-ui
:example:thePremiumThing:feature-.->:example:thePremiumThing:ui
:example:thePremiumThing:feature-.->:example:thePremiumThing:domain
:example:thePremiumThing:ui-.->:example:models
:example:thePremiumThing:ui-.->:example:thePremiumThing:models
:example:thePremiumThing:ui-.->:example:shared-ui
```