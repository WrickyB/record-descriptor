package biswas.wrichik.recorddescriptor;

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.runtime.ObjectMethods;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RecordDescriptor<T extends Record & Constable> {
	private static final MethodHandle GET_DESCRIPTOR;
	private static final MethodHandle TO_STRING;
	private static final MethodHandle EQUALS;
	private static final MethodHandle HASHCODE;
	private static final Map<Input, RecordDescriptor<?>> CACHE = new ConcurrentHashMap<>();
	
	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			MethodHandle nonNull = lookup.findStatic(Objects.class, "nonNull", MethodType.methodType(boolean.class, Object.class));
			MethodHandle returnNull = MethodHandles.dropArguments(MethodHandles.constant(ConstantDesc.class, ConstantDescs.NULL), 0, Object.class);
			MethodHandle describeConstableDirect = lookup.findVirtual(Constable.class, "describeConstable", MethodType.methodType(Optional.class));
			MethodHandle describeConstable = describeConstableDirect.asType(MethodType.methodType(Optional.class, Object.class));
			MethodHandle optionalGetDirect = lookup.findVirtual(Optional.class, "get", MethodType.methodType(Object.class));
			MethodHandle optionalGet = optionalGetDirect.asType(MethodType.methodType(ConstantDesc.class, Optional.class));
			MethodHandle getDescriptor = MethodHandles.filterReturnValue(describeConstable, optionalGet);
			GET_DESCRIPTOR = MethodHandles.guardWithTest(nonNull, getDescriptor, returnNull);
			String names = "constructorDescriptor;componentGetters";
			MethodHandle[] attributeGetters = {lookup.findGetter(RecordDescriptor.class, "constructorDescriptor", DirectMethodHandleDesc.class), lookup.findGetter(RecordDescriptor.class, "componentGetters", MethodHandle[].class)};
			TO_STRING = ((CallSite) ObjectMethods.bootstrap(lookup, "toString", MethodType.methodType(String.class, RecordDescriptor.class), RecordDescriptor.class, names, attributeGetters)).dynamicInvoker();
			EQUALS = ((CallSite) ObjectMethods.bootstrap(lookup, "equals", MethodType.methodType(boolean.class, RecordDescriptor.class, Object.class), RecordDescriptor.class, names, attributeGetters)).dynamicInvoker();
			HASHCODE = ((CallSite) ObjectMethods.bootstrap(lookup, "hashCode", MethodType.methodType(int.class, RecordDescriptor.class), RecordDescriptor.class, names, attributeGetters)).dynamicInvoker();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	private final DirectMethodHandleDesc constructorDescriptor;
	private final MethodHandle[] componentGetters;
	private final MethodHandle toString;
	private final MethodHandle equals;
	private final MethodHandle hashCode;
	
	private RecordDescriptor(DirectMethodHandleDesc constructorDescriptor, MethodHandle[] componentGetters) {
		this.constructorDescriptor = constructorDescriptor;
		this.componentGetters = componentGetters;
		toString = TO_STRING.bindTo(this);
		equals = EQUALS.bindTo(this);
		hashCode = HASHCODE.bindTo(this);
	}
	
	public Optional<DynamicConstantDesc<T>> getDescriptor(T record) {
		Objects.requireNonNull(record, "record");
		try {
			ConstantDesc[] descs = new ConstantDesc[componentGetters.length + 1];
			for (int i = 0; i < componentGetters.length; i++) {
				descs[i + 1] = (ConstantDesc) componentGetters[i].invoke(record);
			}
			descs[0] = constructorDescriptor;
			return Optional.of(DynamicConstantDesc.of(ConstantDescs.BSM_INVOKE, descs));
		} catch (Throwable t) {
			return Optional.empty();
		}
	}
	
	public static <T extends Record & Constable> RecordDescriptor<T> of(MethodHandles.Lookup lookup, Class<T> recordClass) {
		return of(lookup, recordClass, false);
	}
	
	public static <T extends Record & Constable> RecordDescriptor<T> of(MethodHandles.Lookup lookup, Class<T> recordClass, boolean force) {
		return (RecordDescriptor<T>) CACHE.computeIfAbsent(new Input(recordClass, force), input -> {
			Objects.requireNonNull(lookup, "lookup");
			Objects.requireNonNull(recordClass, "recordClass");
			if (!recordClass.isRecord()) {
				throw new IllegalArgumentException("Not a record class");
			}
			if (!Constable.class.isAssignableFrom(recordClass)) {
				throw new IllegalArgumentException("Not a Constable class");
			}
			try {
				lookup.accessClass(recordClass);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			DirectMethodHandleDesc directMethodHandleDesc;
			try {
				directMethodHandleDesc = (DirectMethodHandleDesc) lookup.unreflectConstructor(recordClass.getDeclaredConstructors()[0]).describeConstable().get();
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			RecordComponent[] components = recordClass.getRecordComponents();
			MethodHandle[] descriptorGetters = new MethodHandle[components.length];
			for (int i = 0; i < components.length; i++) {
				RecordComponent component = components[i];
				Class componentType = component.getType();
				if (!Constable.class.isAssignableFrom(componentType)) {
					if (!componentType.equals(components[i].getGenericType())) {
						if (!force) {
							throw new IllegalArgumentException(String.format("Component #%s (%s) is generic, set 'force=%s' to allow", i, components[i], true));
						}
					}
				}
				Method method = component.getAccessor();
				MethodHandle accessorDirect;
				try {
					accessorDirect = lookup.unreflect(method);
				} catch (IllegalAccessException iae) {
					throw new RuntimeException(iae);
				}
				MethodHandle accessor = MethodHandles.filterReturnValue(accessorDirect.asType(accessorDirect.type().changeReturnType(Object.class)), GET_DESCRIPTOR);
				descriptorGetters[i] = accessor;
			}
			return new RecordDescriptor<>(directMethodHandleDesc, descriptorGetters);
		});
	}
	
	public DirectMethodHandleDesc getConstructorDescriptor() {
		return constructorDescriptor;
	}
	
	public MethodHandle[] getComponentGetters() {
		MethodHandle[] getters = new MethodHandle[componentGetters.length];
		System.arraycopy(componentGetters, 0, getters, 0, componentGetters.length);
		return getters;
	}
	
	public String toString() {
		try {
			return (String) toString.invokeExact();
		} catch (Throwable e) {
			return super.toString();
		}
	}
	
	public boolean equals(Object other) {
		try {
			return (boolean) equals.invoke(other);
		} catch (Throwable ignored) {
			return super.equals(other);
		}
	}
	
	public int hashCode() {
		try {
			return (int) hashCode.invokeExact();
		} catch (Throwable e) {
			return System.identityHashCode(this);
		}
	}
	
	private record Input(Class<?> inputClass, boolean force) implements Constable {
		
		private static final RecordDescriptor<Input> DESCRIPTOR = RecordDescriptor.of(MethodHandles.lookup(), Input.class, true);
		
		@Override
		public Optional<DynamicConstantDesc<Input>> describeConstable() {
			return DESCRIPTOR.getDescriptor(this);
		}
	}
}
