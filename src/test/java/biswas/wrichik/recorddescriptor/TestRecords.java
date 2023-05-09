package biswas.wrichik.recorddescriptor;

import java.lang.constant.Constable;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

public class TestRecords {
	
	public record NonConstableRecord() {}
	
	public record ConstableRecord() implements Constable {
		
		private static final DirectMethodHandleDesc CONSTRUCTOR;
		
		static {
			try {
				CONSTRUCTOR = (DirectMethodHandleDesc) MethodHandles.lookup().unreflectConstructor(ConstableRecord.class.getDeclaredConstructors()[0]).describeConstable().get();
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public Optional<DynamicConstantDesc<ConstableRecord>> describeConstable() {
			return Optional.of(DynamicConstantDesc.of(ConstantDescs.BSM_INVOKE, CONSTRUCTOR));
		}
	}
	
	public record GenericConstableRecord<T>(T object) implements Constable {
		
		private static final DirectMethodHandleDesc CONSTRUCTOR;
		
		static {
			try {
				CONSTRUCTOR = (DirectMethodHandleDesc) MethodHandles.lookup().findConstructor(GenericConstableRecord.class, MethodType.methodType(void.class, Object.class)).describeConstable().get();
			} catch (NoSuchMethodException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public Optional<DynamicConstantDesc<GenericConstableRecord>> describeConstable() {
			if (object instanceof Constable) {
				return Optional.of(DynamicConstantDesc.of(ConstantDescs.BSM_INVOKE, CONSTRUCTOR, ((Constable) object).describeConstable().get()));
			} else {
				return Optional.empty();
			}
		}
	}
}
