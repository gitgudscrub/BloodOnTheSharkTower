# A.11 Night Chat roster privacy patch

Apply this patch over the current A.11 development project.

## Behaviour

- During Night, every seated player and Storyteller remains in the shared Simple Voice Chat group even while a Storyteller/private conversation is active.
- Private voice isolation is enforced by filtering group sound packets between the private pair and everyone else.
- As a result, Simple Voice Chat's group-member heads remain stable for the whole night and no longer reveal who has left to speak with the Storyteller.
- A player left in PRIVATE_HOLD remains visible in the Night Chat roster but cannot hear or be heard by the public Night Chat until they leave the hold.
- At Dawn, shared Night Chat is removed normally. Any still-active private conversation is moved into its own private group so phase-independent private chats continue to work.
