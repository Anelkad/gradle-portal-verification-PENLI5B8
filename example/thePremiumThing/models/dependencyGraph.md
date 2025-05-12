
%% Dependents
:example:app-.->:example:ui
:example:app-.->:example:domain
:example:app-.->:example:theNewThing:feature
:example:app-.->:example:thePremiumThing:feature
:example:thePremiumThing:data-.->:example:models
:example:thePremiumThing:data-.->:example:thePremiumThing:models
:example:thePremiumThing:feature-.->:example:thePremiumThing:ui
:example:thePremiumThing:feature-.->:example:thePremiumThing:domain
:example:thePremiumThing:ui-.->:example:models
:example:thePremiumThing:ui-.->:example:thePremiumThing:models
:example:thePremiumThing:ui-.->:example:shared-ui
:example:thePremiumThing:domain-.API.->:example:thePremiumThing:models
:example:thePremiumThing:domain-.->:example:thePremiumThing:data
```