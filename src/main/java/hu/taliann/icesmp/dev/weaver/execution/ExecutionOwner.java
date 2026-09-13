package hu.taliann.icesmp.dev.weaver.execution;

public sealed interface ExecutionOwner permits ActorOwner, EntityOwner, RegionOwner, GlobalOwner, AsyncIoOwner, ProfileOwner {}
