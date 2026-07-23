package org.mpi_sws.jmc.api.mp;

record Envelope(Tid src, Tid dst, CommunicationModel model, Object value, VectorClock clock, long seq) {}