package biswas.wrichik.recorddescriptor;

import java.lang.constant.DynamicConstantDesc;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecordDescriptorTest {
	
	@Test
	public void throwsNPEOnNullLookup() {
		assertThrows(NullPointerException.class, () -> RecordDescriptor.of(null, null));
	}
	
	@Test
	public void throwsNPEOnNullClass() {
		assertThrows(NullPointerException.class, () -> RecordDescriptor.of(MethodHandles.lookup(), null));
	}
	
	@Test
	public void throwIAEOnNonRecord() {
		assertThrows(IllegalArgumentException.class, () -> RecordDescriptor.of(MethodHandles.lookup(),  (Class) String.class));
	}
	
	@Test
	public void throwIAEOnNonConstable() {
		assertThrows(IllegalArgumentException.class, () -> RecordDescriptor.of(MethodHandles.lookup(),  (Class) TestRecords.NonConstableRecord.class));
	}
	
	@Test
	public void testGetDescriptor() {
		Assertions.assertDoesNotThrow(() -> RecordDescriptor.of(MethodHandles.lookup(), TestRecords.ConstableRecord.class));
	}
	
	@Test
	public void testRequireForce() {
		assertThrows(IllegalArgumentException.class, () -> RecordDescriptor.of(MethodHandles.lookup(), TestRecords.GenericConstableRecord.class));
		Assertions.assertDoesNotThrow(() -> RecordDescriptor.of(MethodHandles.lookup(), TestRecords.GenericConstableRecord.class, true));
	}
	
	@Test
	public void testDescriptor() throws Throwable {
		TestRecords.GenericConstableRecord<String> record = new TestRecords.GenericConstableRecord<>("Test");
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		RecordDescriptor<TestRecords.GenericConstableRecord<String>> descriptor = RecordDescriptor.of(lookup, (Class) TestRecords.GenericConstableRecord.class, true);
		Optional<DynamicConstantDesc<TestRecords.GenericConstableRecord<String>>> desc = descriptor.getDescriptor(record);
		assertEquals(record.describeConstable(), desc);
		assertTrue(desc.isPresent());
		assertEquals(record, desc.get().resolveConstantDesc(lookup));
	}
}
