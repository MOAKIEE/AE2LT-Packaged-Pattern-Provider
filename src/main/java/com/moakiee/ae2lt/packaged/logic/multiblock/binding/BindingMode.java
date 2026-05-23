package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

/**
 * Classification of how a bound pattern reaches the target machine.
 *
 * <p>VIRTUAL: the adapter computes outputs without writing to the world; results are
 * accumulated and flushed in batch ticks. No machine state is observed or mutated.
 *
 * <p>REAL: the adapter performs a physical dispatch (item insert / drop / pedestal layout)
 * and the machine consumes real ticks to produce outputs. Push is gated by per-lane cooldown
 * and a pre-dispatch state check.
 */
public enum BindingMode {
    VIRTUAL,
    REAL
}
