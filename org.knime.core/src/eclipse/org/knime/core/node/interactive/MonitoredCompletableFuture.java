/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   17 Apr 2018 (albrecht): created
 */
package org.knime.core.node.interactive;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeProgressListener;

/**
 * A {@link Future} that may be explicitly completed (setting its
 * value and status), and may be used as a {@link CompletionStage},
 * supporting dependent functions and actions that trigger upon its
 * completion.
 * Additionally this {@link Future} holds an {@link ExecutionMonitor} used
 * to query progress and check for cancellation.
 *
 * @see CompletableFuture
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 * @param <T> The result type returned by this future.
 * @since 3.6
 */
public class MonitoredCompletableFuture<T> extends CompletableFuture<T> {

    private final CompletableFuture<T> m_baseFuture;

    private final Executor m_executor;

    private final ExecutionMonitor m_monitor;

    /**
     * Create a new future object.
     * @param baseFuture a {@link CompletableFuture} which is the base for this future, not null
     * @param executor an {@link Executor} used for the completion of the future, not null
     * @param monitor an {@link ExecutionMonitor} used to query progress, not null
     */
    public MonitoredCompletableFuture(final CompletableFuture<T> baseFuture, final Executor executor,
        final ExecutionMonitor monitor) {
        m_baseFuture = CheckUtils.checkNotNull(baseFuture);
        m_executor = CheckUtils.checkNotNull(executor);
        m_monitor = CheckUtils.checkNotNull(monitor);
    }

    /**
     * @return the {@link ExecutionMonitor} of this future
     */
    public ExecutionMonitor getMonitor() {
        return m_monitor;
    }

    /**
     * @return progress (between 0.0 and 1.0), if available
     */
    public Optional<Double> getProgress() {
        if (m_monitor.getProgressMonitor() != null) {
            return Optional.ofNullable(m_monitor.getProgressMonitor().getProgress());
        }
        return Optional.empty();
    }

    /**
     * @return the current progress message, if available
     */
    public Optional<String> getProgressMessage() {
        if (m_monitor.getProgressMonitor() != null) {
            return Optional.ofNullable(m_monitor.getProgressMonitor().getMessage());
        }
        return Optional.empty();
    }

    /**
     * Adds a new listener to the list of instances which are interested in receiving progress events.
     * @param listener The listener to add
     * @return true if the listener was successfully registered, false otherwise
     */
    public boolean addProgressListener(final NodeProgressListener listener) {
        if (m_monitor.getProgressMonitor() != null) {
            m_monitor.getProgressMonitor().addProgressListener(listener);
            return true;
        }
        return false;
    }

    //it is ok to assume the common pool here, as the base future will always screen the executor and assign
    //a ThreadPerTaskExecutor if the common pool does not support parallelism
    private static final Executor asyncPool = ForkJoinPool.commonPool();

    /**
     * Returns a new MonitoredCompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} with
     * the value obtained by calling the given Supplier.
     *
     * @param s a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param monitor an {@link ExecutionMonitor} used to query progress
     * @param <U> the function's return type
     * @return the new MonitoredCompletableFuture
     * @see CompletableFuture#supplyAsync(Supplier)
     */
    public static <U> MonitoredCompletableFuture<U> supplyAsync(final Supplier<U> s, final ExecutionMonitor monitor) {
        return monitor(CompletableFuture.supplyAsync(s), asyncPool, monitor);
    }

    /**
     * Returns a new MonitoredCompletableFuture that is asynchronously completed
     * by a task running in the given executor with the value obtained
     * by calling the given Supplier.
     *
     * @param s a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param e the executor to use for asynchronous execution
     * @param monitor an {@link ExecutionMonitor} used to query progress
     * @param <U> the function's return type
     * @return the new MonitoredCompletableFuture
     * @see CompletableFuture#supplyAsync(Supplier, Executor)
     */
    public static <U> MonitoredCompletableFuture<U> supplyAsync(final Supplier<U> s, final Executor e,
        final ExecutionMonitor monitor) {
        return monitor(CompletableFuture.supplyAsync(s, e), e, monitor);
    }

    /**
     * Returns a new MonitoredCompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} after
     * it runs the given action.
     *
     * @param r the action to run before completing the
     * returned CompletableFuture
     * @param monitor an {@link ExecutionMonitor} used to query progress
     * @return the new CompletableFuture
     * @see CompletableFuture#runAsync(Runnable)
     */
    public static MonitoredCompletableFuture<Void> runAsync(final Runnable r, final ExecutionMonitor monitor) {
        return monitor(CompletableFuture.runAsync(r), asyncPool, monitor);
    }

    /**
     * Returns a new MonitoredCompletableFuture that is asynchronously completed
     * by a task running in the given executor after it runs the given
     * action.
     *
     * @param r the action to run before completing the
     * returned CompletableFuture
     * @param e the executor to use for asynchronous execution
     * @param monitor an {@link ExecutionMonitor} used to query progress
     * @return the new CompletableFuture
     * @see CompletableFuture#runAsync(Runnable, Executor)
     */
    public static MonitoredCompletableFuture<Void> runAsync(final Runnable r, final Executor e,
        final ExecutionMonitor monitor) {
        return monitor(CompletableFuture.runAsync(r), e, monitor);
    }

    /**
     * Returns a new MonitoredCompletableFuture that is already completed with
     * the given value.
     *
     * @param value the value
     * @param monitor an {@link ExecutionMonitor} used to query progress
     * @param <U> the type of the value
     * @return the completed CompletableFuture
     * @see CompletableFuture#completedFuture(Object)
     */
    public static <U> MonitoredCompletableFuture<U> completedFuture(final U value, final ExecutionMonitor monitor) {
        return monitor(CompletableFuture.completedFuture(value), asyncPool, monitor);
    }

    private <U> MonitoredCompletableFuture<U> monitor(final CompletableFuture<U> base) {
        return monitor(base, m_executor, m_monitor);
    }

    private <U> MonitoredCompletableFuture<U> monitor(final CompletableFuture<U> base, final Executor executor) {
        return monitor(base, executor, m_monitor);
    }

    private static <U> MonitoredCompletableFuture<U> monitor(final CompletableFuture<U> f, final Executor e,
        final ExecutionMonitor monitor) {
        MonitoredCompletableFuture<U> monitoredFuture = new MonitoredCompletableFuture<U>(f, e, monitor);
        f.whenComplete((value, throwable) -> {
            if (throwable != null) {
                monitoredFuture.completeExceptionally(throwable);
            } else {
                monitoredFuture.complete(value);
            }
        });
        return monitoredFuture;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> acceptEither(final CompletionStage<? extends T> other,
        final Consumer<? super T> action) {
        return monitor(m_baseFuture.acceptEither(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
        final Consumer<? super T> action) {
        return monitor(m_baseFuture.acceptEitherAsync(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
        final Consumer<? super T> action, final Executor executor) {
        return monitor(m_baseFuture.acceptEitherAsync(other, action, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> applyToEither(final CompletionStage<? extends T> other,
        final Function<? super T, U> fn) {
        return monitor(m_baseFuture.applyToEither(other, fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
        final Function<? super T, U> fn) {
        return monitor(m_baseFuture.applyToEitherAsync(other, fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> applyToEitherAsync(final CompletionStage<? extends T> other,
        final Function<? super T, U> fn, final Executor executor) {
        return monitor(m_baseFuture.applyToEitherAsync(other, fn, executor));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        if (m_monitor.getProgressMonitor() != null) {
            m_monitor.getProgressMonitor().setExecuteCanceled();
        }
        return super.cancel(mayInterruptIfRunning);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        return monitor(m_baseFuture.exceptionally(fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return monitor(m_baseFuture.handle(fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return monitor(m_baseFuture.handleAsync(fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
        final Executor executor) {
        return monitor(m_baseFuture.handleAsync(fn, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> runAfterBoth(final CompletionStage<?> other, final Runnable action) {
        return monitor(m_baseFuture.runAfterBoth(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action) {
        return monitor(m_baseFuture.runAfterBothAsync(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action,
        final Executor executor) {
        return monitor(m_baseFuture.runAfterBothAsync(other, action, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> runAfterEither(final CompletionStage<?> other, final Runnable action) {
        return monitor(m_baseFuture.runAfterEither(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action) {
        return monitor(m_baseFuture.runAfterEitherAsync(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action,
        final Executor executor) {
        return monitor(m_baseFuture.runAfterEitherAsync(other, action, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> thenAccept(final Consumer<? super T> action) {
        return monitor(m_baseFuture.thenAccept(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action) {
        m_baseFuture.thenAccept
        return monitor(m_baseFuture.thenAcceptAsync(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> thenAcceptAsync(final Consumer<? super T> action, final Executor executor) {
        return monitor(m_baseFuture.thenAcceptAsync(action, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
        final BiConsumer<? super T, ? super U> action) {
        return monitor(m_baseFuture.thenAcceptBoth(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
        final BiConsumer<? super T, ? super U> action) {
        return monitor(m_baseFuture.thenAcceptBothAsync(other, action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
        final BiConsumer<? super T, ? super U> action, final Executor executor) {
        return monitor(m_baseFuture.thenAcceptBothAsync(other, action, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
        return monitor(m_baseFuture.thenApply(fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        return monitor(m_baseFuture.thenApplyAsync(fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> thenApplyAsync(final Function<? super T, ? extends U> fn,
        final Executor executor) {
        return monitor(m_baseFuture.thenApplyAsync(fn, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U, V> MonitoredCompletableFuture<V> thenCombine(final CompletionStage<? extends U> other,
        final BiFunction<? super T, ? super U, ? extends V> fn) {
        return monitor(m_baseFuture.thenCombine(other, fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U, V> MonitoredCompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
        final BiFunction<? super T, ? super U, ? extends V> fn) {
        return monitor(m_baseFuture.thenCombineAsync(other, fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U, V> MonitoredCompletableFuture<V> thenCombineAsync(final CompletionStage<? extends U> other,
        final BiFunction<? super T, ? super U, ? extends V> fn, final Executor executor) {
        return monitor(m_baseFuture.thenCombineAsync(other, fn, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return monitor(m_baseFuture.thenCompose(fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U>
        thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return monitor(m_baseFuture.thenComposeAsync(fn));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public <U> MonitoredCompletableFuture<U>
        thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn, final Executor executor) {
        return monitor(m_baseFuture.thenComposeAsync(fn, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> thenRun(final Runnable action) {
        return monitor(m_baseFuture.thenRun(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> thenRunAsync(final Runnable action) {
        return monitor(m_baseFuture.thenRunAsync(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<Void> thenRunAsync(final Runnable action, final Executor executor) {
        return monitor(m_baseFuture.thenRunAsync(action, executor), executor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        return monitor(m_baseFuture.whenComplete(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        return monitor(m_baseFuture.whenCompleteAsync(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned future will retain the {@link ExecutionMonitor} of this future instance.
     * </p>
     */
    @Override
    public MonitoredCompletableFuture<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
        final Executor executor) {
        return monitor(m_baseFuture.whenCompleteAsync(action, executor), executor);
    }
}
