/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts;

import core.*;
import core.TransactionForVerification.*;
import org.jetbrains.annotations.*;

import java.security.*;
import java.time.*;
import java.util.*;

import static core.ContractsDSLKt.*;
import static kotlin.collections.CollectionsKt.*;

/**
 * This is a Java version of the CommercialPaper contract (chosen because it's simple). This demonstrates how the
 * use of Kotlin for implementation of the framework does not impose the same language choice on contract developers.
 *
 * NOTE: For illustration only. Not unit tested.
 */
public class JavaCommercialPaper implements Contract {
    public static class State implements ContractState {
        private PartyReference issuance;
        private PublicKey owner;
        private Amount faceValue;
        private Instant maturityDate;

        public State() {}  // For serialization

        public State(PartyReference issuance, PublicKey owner, Amount faceValue, Instant maturityDate) {
            this.issuance = issuance;
            this.owner = owner;
            this.faceValue = faceValue;
            this.maturityDate = maturityDate;
        }

        public PartyReference getIssuance() {
            return issuance;
        }

        public PublicKey getOwner() {
            return owner;
        }

        public Amount getFaceValue() {
            return faceValue;
        }

        public Instant getMaturityDate() {
            return maturityDate;
        }

        @NotNull
        @Override
        public SecureHash getProgramRef() {
            return SecureHash.Companion.sha256("java commercial paper (this should be a bytecode hash)");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            State state = (State) o;

            if (issuance != null ? !issuance.equals(state.issuance) : state.issuance != null) return false;
            if (owner != null ? !owner.equals(state.owner) : state.owner != null) return false;
            if (faceValue != null ? !faceValue.equals(state.faceValue) : state.faceValue != null) return false;
            return !(maturityDate != null ? !maturityDate.equals(state.maturityDate) : state.maturityDate != null);

        }

        @Override
        public int hashCode() {
            int result = issuance != null ? issuance.hashCode() : 0;
            result = 31 * result + (owner != null ? owner.hashCode() : 0);
            result = 31 * result + (faceValue != null ? faceValue.hashCode() : 0);
            result = 31 * result + (maturityDate != null ? maturityDate.hashCode() : 0);
            return result;
        }

        public State withoutOwner() {
            return new State(issuance, NullPublicKey.INSTANCE, faceValue, maturityDate);
        }
    }

    public static class Commands implements core.CommandData {
        public static class Move extends Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Move;
            }
        }

        public static class Redeem extends Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Redeem;
            }
        }
        public static class Issue extends Commands {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Redeem;
            }
        }
    }

    @Override
    public void verify(@NotNull TransactionForVerification tx) {
        // There are two possible things that can be done with CP. The first is trading it. The second is redeeming it
        // for cash on or after the maturity date.
        List<InOutGroup<State>> groups = tx.groupStates(State.class, State::withoutOwner);

        // Find the command that instructs us what to do and check there's exactly one.
        AuthenticatedObject<CommandData> cmd = requireSingleCommand(tx.getCommands(), Commands.class);

        TimestampCommand timestampCommand = tx.getTimestampBy(DummyTimestampingAuthority.INSTANCE.getIdentity());
        if (timestampCommand == null)
            throw new IllegalArgumentException("must be timestamped");
        Instant time = timestampCommand.getMidpoint();

        for (InOutGroup<State> group : groups) {
            List<State> inputs = group.getInputs();
            List<State> outputs = group.getOutputs();

            // For now do not allow multiple pieces of CP to trade in a single transaction. Study this more!
            State input = single(filterIsInstance(inputs, State.class));

            if (!cmd.getSigners().contains(input.getOwner()))
                throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

            if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Move) {
                // Check the output CP state is the same as the input state, ignoring the owner field.
                State output = single(outputs);

                if (!output.getFaceValue().equals(input.getFaceValue()) ||
                        !output.getIssuance().equals(input.getIssuance()) ||
                        !output.getMaturityDate().equals(input.getMaturityDate()))
                    throw new IllegalStateException("Failed requirement: the output state is the same as the input state except for owner");
            } else if (cmd.getValue() instanceof JavaCommercialPaper.Commands.Redeem) {
                Amount received = CashKt.sumCashOrNull(inputs);
                if (received == null)
                    throw new IllegalStateException("Failed requirement: no cash being redeemed");
                if (input.getMaturityDate().isAfter(time))
                    throw new IllegalStateException("Failed requirement: the paper must have matured");
                if (!input.getFaceValue().equals(received))
                    throw new IllegalStateException("Failed requirement: the received amount equals the face value");
                if (!outputs.isEmpty())
                    throw new IllegalStateException("Failed requirement: the paper must be destroyed");
            }
        }
    }

    @NotNull
    @Override
    public SecureHash getLegalContractReference() {
        // TODO: Should return hash of the contract's contents, not its URI
        return SecureHash.Companion.sha256("https://en.wikipedia.org/wiki/Commercial_paper");
    }
}
