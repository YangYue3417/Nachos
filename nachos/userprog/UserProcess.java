package nachos.userprog;

import nachos.machine.*;
import nachos.machine.Kernel;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.NoSuchElementException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */



public class UserProcess {
	/**
	 * Allocate a new process.
	 */

	// parameters and functions for part 1
	private OpenFile[] fdTable = new OpenFile[16];

	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		fdTable = new OpenFile[16];
		fdTable[0] = UserKernel.console.openForReading(); // stdin
		fdTable[1] = UserKernel.console.openForWriting(); // stdout

		staticVarLock.acquire();
		PID = globalPID++;
		numProcesses++;
		staticVarLock.release();
		joinCondition = new Condition(joinLock);
	}

	private int handleCreate(int vaName) {
		if(vaName < 0) return -1;
		String fileName = readVirtualMemoryString(vaName, 256);
		if(fileName == null || fileName.length() <= 0) return -1;
		
		// check availability
		int index = 17;
		for(int i=2; i<16;i++) {
			if(fdTable[i]==null) {
				index=i;
				break;
			}
		}
		
		if(index == 17) return -1;
		
		OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
		if(file == null) {
			return -1;
		}
		else {
			fdTable[index] = file;
			return index;
		}
	}
	
	private int handleOpen(int vaName) {
		if(vaName < 0) return -1;
		String fileName = readVirtualMemoryString(vaName, 256);
		if(fileName == null || fileName.length() <= 0) return -1;
		
		// check availability
		int index = 17;
		for(int i=2; i<16;i++) {
			if(fdTable[i]==null) {
				index=i;
				break;
			}
		}
		
		if(index == 17) return -1;
		
		OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
		if(file == null) {
			return -1;
		}
		else {
			fdTable[index] = file;
			return index;
		}
	}
	
	private int handleClose(int fd) {
		if(fd<0 || fd>15 || fdTable[fd]==null) {
			return -1;
		}
		// Do we need synchronization here????
		// boolean Pstatus=Machine.interrupt().disable();
		fdTable[fd].close();
		fdTable[fd] = null;
		// Machine.interrupt().restore(Pstatus);
		
		return 0;
	}
	
	private int handleUnlink(int vaName) {
		if(vaName < 0) return -1;
		String fileName = readVirtualMemoryString(vaName, 256);
		if(fileName == null || fileName.length() <= 0) return -1;
		
		boolean remove = ThreadedKernel.fileSystem.remove(fileName);
		
		
		if(remove) {
			return 0;
		}
		else {
			return -1;
		}
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	    String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 handleRead
	 Use fileTable[fd].read() to read from file to a local buffer of limited size
	 Then write it into the user inputted buffer using writeVirtualMemory()

	 @return total number of bytes that has been read from buffer
	 **/
		private int handleRead(int fd, int buffer, int count) {
		if (fd<0 || fd>15) {
			return -1;
		}
		OpenFile openFile = fdTable[fd];
		if (openFile==null) {
			return -1;
		}
		if (count < 0 || count > pageTable.length*pageSize) {
			return -1;
		}
		byte[] pageSizeArray = new byte[pageSize];
		int readCount = 0;
		while (count > pageSize) {
			int oneTurnRead = openFile.read(pageSizeArray,0,pageSize);
			if (oneTurnRead == 0 ) return readCount;
			if (oneTurnRead < 0) {
				return -1;
			}
			int oneTurnWrite = writeVirtualMemory(buffer,pageSizeArray,0,oneTurnRead);
			if (oneTurnRead < oneTurnWrite) {
				return -1;
			}
			oneTurnRead = Math.min(oneTurnRead, oneTurnWrite);
			buffer += oneTurnRead;
			readCount += oneTurnRead;
			count -= oneTurnRead;
		}
		int oneTurnRead = openFile.read(pageSizeArray,0,count);
		if (oneTurnRead < 0) {
			return -1;
		}
		int oneTurnWrite = writeVirtualMemory(buffer,pageSizeArray,0,oneTurnRead);
		if (oneTurnRead < oneTurnWrite) {
			return -1;
		}
		oneTurnRead = Math.min(oneTurnRead, oneTurnWrite);
		readCount += oneTurnRead;
		count -= oneTurnRead;
		return readCount;
	}

	/**
	 * handleWrite
	 * Read data from the user inputted buffer to a local buffer of limited size.
	 * Use readVirtualMemory()
	 * Then use fileTable[fd].write() to write to file from the local buffer
	 *
	 * @return total bytes that has been written into memory
	 */
	private int handleWrite(int fd, int buffer, int count) {
		if (fd<0 || fd>15 || fdTable[fd]==null) {return -1;}
		byte[] pageSizeArray = new byte[pageSize];
		int totalWrite = 0;
		int currRead = 0;
		int currWrite = 0;
		int leftUnwrite = count;
		while (leftUnwrite > pageSize) {
			currRead = readVirtualMemory(buffer,pageSizeArray);
			currWrite = fdTable[fd].write(pageSizeArray,0,currRead);
			if(currRead != pageSize || currWrite < 0 || currRead != currWrite){
				return -1;
			}
			if (currWrite == 0 ) {
				return totalWrite;
			}

			buffer += currWrite;
			totalWrite += currWrite;
			leftUnwrite -= currWrite;
		}
		if (leftUnwrite<0) {return -1;}
		pageSizeArray = new byte[leftUnwrite];
		currRead = readVirtualMemory(buffer,pageSizeArray);
		currWrite = fdTable[fd].write(pageSizeArray,0,currRead);
		if (currWrite < 0 || currRead!=currWrite) {return -1;}
		totalWrite += currWrite;
		leftUnwrite -= currWrite;
		if (leftUnwrite!=0 || currWrite > pageTable.length*pageSize) {return -1;}
		return totalWrite;
}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		int readleft = 0;
		int totalRead = 0;

		if(offset < 0 || length < 0 || offset + length > data.length){
			return totalRead;
		}

		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr > pageTable.length * pageSize) {
			return totalRead;
		}

		int startVpn = Processor.pageFromAddress(vaddr);
		if (startVpn >= pageTable.length) {
			return totalRead;
		}
		TranslationEntry entry = pageTable[startVpn];
		int pageOffset = Processor.offsetFromAddress(vaddr);
		int physicalAddress = entry.ppn * pageSize + pageOffset;

		while (totalRead < length) {
			if (physicalAddress < 0 || physicalAddress >= memory.length || !entry.valid) {
				break;
			}

			readleft = Math.min(length - totalRead, pageSize - pageOffset);
			System.arraycopy(memory, physicalAddress, data, offset, readleft);
			vaddr += readleft;
			int currentVpn = Processor.pageFromAddress(vaddr);
			if (currentVpn >= pageTable.length) {
				break;
			}

			entry = pageTable[Processor.pageFromAddress(vaddr)];
			pageOffset = Processor.offsetFromAddress(vaddr);
			physicalAddress = entry.ppn * pageSize + pageOffset;
			offset += readleft;
			totalRead += readleft;
		}
		return totalRead;
	}


	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		int writeleft = 0;
		int totalWrite = 0;

		if(offset < 0 || length < 0 || offset + length > data.length){
			return totalWrite;
		}
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr > pageTable.length * pageSize) {
			return totalWrite;
		}
		int startVpn = Processor.pageFromAddress(vaddr);
		if (startVpn >= pageTable.length) {
			return totalWrite;
		}
		TranslationEntry entry = pageTable[startVpn];
		int pageOffset = Processor.offsetFromAddress(vaddr);
		int physicalAddress = entry.ppn * pageSize + pageOffset;

		while (totalWrite < length) {
			if (physicalAddress < 0 || physicalAddress >= memory.length || !entry.valid || entry.readOnly) {
				break;
			}

			writeleft = Math.min(length - totalWrite, pageSize - pageOffset);
			System.arraycopy(data, offset, memory, physicalAddress, writeleft);
			vaddr += writeleft;
			int currentVpn = Processor.pageFromAddress(vaddr);
			if (currentVpn >= pageTable.length) {
				break;
			}

			entry = pageTable[Processor.pageFromAddress(vaddr)];
			pageOffset = Processor.offsetFromAddress(vaddr);
			physicalAddress = entry.ppn * pageSize + pageOffset;
			offset += writeleft;
			totalWrite += writeleft;
		}
		return totalWrite;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// pageTable initialization
		pageTable = new TranslationEntry[numPages];
		UserKernel.freePageMutex.P();
		for (int i = 0; i < numPages; i++){
			pageTable[i] = new TranslationEntry(i, UserKernel.freePageList.remove(), true, false, false, false);
		}
		UserKernel.freePageMutex.V();

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				// get the ppn and map to vpn
				int vpn = section.getFirstVPN() + i;

				UserKernel.freePageMutex.P();
				try{
					TranslationEntry entry = pageTable[vpn];
					section.loadPage(i, entry.ppn);
					entry.readOnly = section.isReadOnly();
				}
				catch (NoSuchElementException e){
					unloadSections();
					UserKernel.freePageMutex.V();
					return false;
				}
				UserKernel.freePageMutex.V();

			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < pageTable.length; i++){
			if(pageTable[i]!=null && pageTable[i].valid){
				UserKernel.freePageMutex.P();
				pageTable[i].used = false;
				UserKernel.freePageList.add(pageTable[i].ppn);
				UserKernel.freePageMutex.V();
				pageTable[i] = null;
			}
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int file, int argc, int argv) {
		String fileName = readVirtualMemoryString(file, 256);
		if( fileName == null || argc < 0 || argv < 0|| argv > numPages*pageSize)
			return -1;
		String[] args = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] argsAddress = new byte[4];
			if (readVirtualMemory(argv+i*4, argsAddress) > 0)
				args[i] = readVirtualMemoryString(nachos.machine.Lib.bytesToInt(argsAddress, 0), 256);
		}
		UserProcess childProcess = UserProcess.newUserProcess();

		//	Run our child process. If execution fails, return -1.
		if (!childProcess.execute(fileName, args))
			return -1;
		//	set the child process's parent and write it to map for tracking
		childProcess.parentProcess = this;
		childMap.put(childProcess.PID, childProcess);

		return childProcess.PID;
	}

	/**
	 * Handle the join() system call.
	 */
	private int handleJoin(int pid, int status) {
		int vpn = nachos.machine.Processor.pageFromAddress(status);
		if (vpn < 0 || vpn >= numPages) {
			handleExit(null);
			return -1;
		}
		UserProcess childProcess = childMap.get(pid);
		if (childProcess == null)
			return -1;

		// Acquire lock for join. Put this process to sleep until it is waken up by its child process.
		childProcess.joinLock.acquire();
		while(!childProcess.is_exit)
			childProcess.joinCondition.sleep();
		childProcess.joinLock.release();

		// Remove child from map
		childMap.remove(pid);

		// Get the return value of child process. Deal with the return value.
		if (childStatus.get(pid) == null) {
			childMap.remove(pid);
			return 0;
		}
		else {
			writeVirtualMemory(status, nachos.machine.Lib.bytesFromInt(childStatus.get(pid)));
			childMap.remove(pid);
			return 1;
		}
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(Integer status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		this.joinLock.acquire();
		// If this process has a parent, save its status for exiting.
		if (parentProcess != null) {
			UserProcess currentProcess = parentProcess.childMap.get(PID);
			currentProcess = null;
			if (is_exception)
				parentProcess.childStatus.put(PID, null);
			else
				parentProcess.childStatus.put(PID, status);
		}
		// Update all child processes of current process and set their parent to be null
		// since current process is existing.
		for (UserProcess childProcess : childMap.values()) {
			if (childProcess != null) {
				childProcess.parentProcess = null;
			}
		}
		// Clean the child map for current process.
		childMap = null;

		// Close all opened files.
		for (int fd = 0; fd < fdTable.length; fd++)
			if (fdTable[fd] != null)
				handleClose(fd);

		// Free virtual memory used by current process.
		unloadSections();
		coff.close();

		// Wake up parent process.
		is_exit = true;
		joinCondition.wakeAll();
		this.joinLock.release();

		// Check if we should terminate the machine.
		staticVarLock.acquire();
		if (--numProcesses == 0)
			Kernel.kernel.terminate();
		staticVarLock.release();
		
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		nachos.threads.KThread.finish();
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		// case for part 1
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			is_exception = true;
			handleExit(cause);
			// Kernel.kernel.terminate();
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;

	/** Lock for the static variables */
	private static Lock staticVarLock = new Lock();
	/** Counter of processes */
	private static int numProcesses = 0;
	/** Global PID for creating PIDs (must be protected by lock while using) */
	private static int globalPID = 0;
	/** This process's PID. */
	protected int PID;
	/** This process's parent. */
	protected UserProcess parentProcess;
	/** This process's childMap including their PID and process to track its children. */
	private HashMap<Integer, UserProcess> childMap = new HashMap<Integer, UserProcess> ();
	/** This process's childMap including their PID and return status. */
	private HashMap<Integer, Integer> childStatus = new HashMap<Integer, Integer> ();

	/** Join condition variable, lock and status */
	private boolean is_exit = false;
	private Condition joinCondition;
	private Lock joinLock = new Lock();

	private boolean is_exception = false;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
}
